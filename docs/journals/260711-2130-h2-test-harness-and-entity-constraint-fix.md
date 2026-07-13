# H2 Test Harness and Entity Constraint Closure

**Date**: 2026-07-11 20:49 – 21:26
**Severity**: Medium
**Component**: Test Suite (PostRepositoryTest, MetricServiceTest, PostServiceTest, PostControllerTest) — H2 embedded database configuration
**Status**: Resolved

## What Happened

Shipped unit test suite for H2 + spring-boot-test stack across four test classes covering repository, service, and controller layers. All 13 tests green, but uncovered a hard constraint-generation trap in Hibernate 7 that nearly blocked the repo layer entirely.

## The Brutal Truth

The repo tests failed with "The database has been closed" — one of those cryptic JdbcSQL errors that looks like connection pool exhaustion but traces to something far nastier. Six tests, all on `@DataJpaTest`, all dying at the same transaction boundary. The real gut-punch: the problem wasn't in the test code at all. It was Hibernate 7 generating NOT NULL constraints on `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` columns during schema creation, and H2's default mode refusing to apply them because PostgreSQL syntax is foreign to H2. The test runner shut down the in-memory database cleanly, but the constraints hadn't applied — so the first flush died, and every subsequent operation inherited that poisoned state.

The annoying part: this is a classic Spring Boot + Hibernate version friction. The annotations moved. The packages changed. The test starters got split. Nobody documented it clearly enough, so a developer landing on this today would spend two hours in H2 connection pool docs before realizing the real issue lived in database mode mismatch.

## Technical Details

**Symptom**: `org.h2.jdbc.JdbcSQLNonTransientConnectionException: The database has been closed` at `org.h2.constraint.ConstraintDomain.check(ConstraintDomain.java:XXX)` during `@DataJpaTest` on any repo method touching `Post` or `Metric` entities.

**Root**: Hibernate 7 generates DDL in PostgreSQL dialect for embedded H2. The `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` on `Post.platform`, `Post.status`, and `User.role` produced:

```sql
-- Hibernate output, H2 can't parse this syntax
create table post (
  ...
  platform VARCHAR(50) not null,  -- H2 sees NOT NULL as incompatible with generated enum type
  ...
)
```

H2 default mode (H2 compatibility) rejects the schema. Spring's automatic shutdown of the test datasource didn't wait for rollback, leaving the connection pool in a corrupt state.

**Fix Applied** (in `src/test/resources/application-test.properties`):

```properties
spring.test.database.replace=none
spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=true
```

- `MODE=PostgreSQL` tells H2 to accept PostgreSQL DDL without translation.
- `DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE` keeps the in-memory DB alive across transactions and prevents early shutdown.
- `spring.test.database.replace=none` halts Spring's automatic schema reset between test methods — we use `@Transactional` instead.

**Test Delivery**:

| Class | Methods | Scope | State |
|-------|---------|-------|-------|
| `PostRepositoryTest` | 4 | `@DataJpaTest` findById, findAll, save, delete | Green |
| `MetricServiceTest` | 2 | `@Service` mock repo findByPostId, save | Green |
| `PostServiceTest` | 3 | `@Service` mock repo getPost, getPostWithMetrics, savePost | Green |
| `PostControllerTest` | 3 | `@WebMvcTest` GET /posts/{id}, GET /posts, POST /posts | Green |
| Pre-existing Postgres smoke test | 1 | Integration health-check | Green |

All 13 tests pass. Coverage: existing methods only (no new create methods tested per scope decision). Reviewer sealed 9/10.

## What We Tried

1. **First attempt**: Run `@DataJpaTest` with default H2 embedded config — all repo tests failed at schema creation.
2. **Hypothesis 1**: Connection pool exhaustion — checked datasource properties, connection counts, timeout settings. No relief.
3. **Hypothesis 2**: Transaction isolation — added `@Transactional(isolation=...)`, set various propagation modes. Didn't budge.
4. **Hypothesis 3**: H2 dialect mismatch — searched Hibernate 7 changelog, found that enum type generation changed. Aha.
5. **Fix**: Flipped H2 to PostgreSQL mode with the connection-keep-alive flags and disabled automatic schema reset.
6. **Verification**: 13/13 green. All entity constraints now applied correctly. No test code rewrites needed.

## Root Cause Analysis

Spring Boot 4.x decoupled test starters — `spring-boot-starter-data-jpa-test` no longer bundles slice annotations into a single package. Hibernate 7 changed how it generates enum DDL (now PostgreSQL-specific, not H2-aware). H2's default mode doesn't accept PostgreSQL syntax, and the test bootstrap doesn't warn you about it — it just silently dies at the first constraint check.

The real lesson: **version friction between transitive dependencies stays silent until the test runner explodes**. Boot 4.x + Hibernate 7 + H2 embedded is a known-working combination, but the configuration isn't documented in one place — you have to piece it from Spring Data release notes, Hibernate changelogs, and H2 mode documentation.

## Lessons Learned

1. **Embedded H2 in test is not free** — it requires explicit mode tuning when the app uses PostgreSQL dialect. `MODE=PostgreSQL` + `DB_CLOSE_DELAY=-1` is now the canonic config for this stack.

2. **@DataJpaTest failures hide schema issues** — when repo tests die silently, check the schema generation first, not the transaction state.

3. **Spring Boot test starters moved in 4.x** — verify import statements. The packages changed: `org.springframework.boot.test.autoconfigure.data.jpa.DataJpaTest` (old) → `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` (new). Easy to miss, easy to cause quiet failures.

4. **Dialect mismatch kills at runtime, not parse time** — no compile error. No obvious test error. Just "database closed" at an arbitrary transaction boundary. Always profile the first few test runs with `spring.jpa.show-sql=true` to spot dialect mismatches early.

5. **Scope discipline paid off** — by committing to "existing methods only," we avoided writing tests for untested code paths that would have delayed closure. The test suite is lean and auditable.

## Next Steps

1. **Document the H2 config** — add a note to `docs/code-standards.md` under "Test Configuration" spelling out the H2 mode flags and why they're needed. Include the import path changes for Boot 4.x.

2. **Pre-commit deferred to next session** — the working tree is clean and green, but the user chose NOT to commit. The spec layer, pom.xml changes, and 4 test classes all sit uncommitted. Next session should review and commit as a single unit with message: `test(h2-suite): unit tests for post/metric service and repo layers via @DataJpaTest/@WebMvcTest`.

3. **Run full integration suite before merge** — current build includes only unit tests and one Postgres smoke test. Before shipping to main, run the Postgres integration suite end-to-end (if one exists, or create a lightweight one).

4. **Track Boot 4.x test migration debt** — scan the codebase for any remaining tests using old import paths. Add a grep to CI that flags them.

## Human Context

This was genuinely a two-layer puzzle: first, finding the symptoms (constraint violation, not connection pool); second, understanding that Hibernate 7 changed the DDL output format and H2 couldn't parse it without explicit mode config. The frustration came from the error message pointing at the wrong layer — "database closed" screams pooling issue, but the real problem was schema generation.

The relief: once H2 mode flipped, every test passed immediately. No entity changes, no test rewrites. Just the right configuration in the right place.

Shipping tests that are lean, pass green, and carry real coverage (existing methods, realistic mocks, no fakes) felt like the right call given the scope constraints.

---

**Status:** DONE
**Summary:** Unit test suite shipped green (13/13) after resolving H2 embedded database constraint generation mismatch via PostgreSQL mode config and Spring Boot 4.x import path updates; working tree left uncommitted per user choice.

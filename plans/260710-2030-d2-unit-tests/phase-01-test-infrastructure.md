# Phase 01 — Test Infrastructure (D2-01)

## Context Links
- Spec: `spec/post-metric-test-suite/technical-spec.md` (FR-001, US001, SC-001)
- Edge cases: `spec/post-metric-test-suite/edge-cases.md` (rows 4–5: Flyway-on-H2 failure, NOT NULL)
- Scout: `plans/reports/researcher-260710-2030-d2-test-scout.md` (Build section)

## Overview
- **Priority:** P1
- **Status:** completed
- **Goal:** Make `@DataJpaTest` bootable on H2 without Postgres. Add H2 test-scoped dep + a
  test profile that disables Flyway so Hibernate `create-drop` generates the schema.

## Requirements
- **FR-001** — H2 in-memory DB at test scope; Flyway disabled in the JPA slice.
- `spring-boot-starter-test` is already present — do NOT re-add it.
- H2 MUST be `<scope>test</scope>` so it is absent from the packaged production JAR.

## Data Flow
`./mvnw test` → Maven resolves H2 (test scope) → `@DataJpaTest` picks embedded H2 datasource
→ `spring.flyway.enabled=false` skips `V1__init_schema.sql` (Postgres-only DDL) → Hibernate
`create-drop` builds schema from entity mappings → tests run → schema dropped.

## Related Code Files
**Modify:**
- `pom.xml` — add `com.h2database:h2` with `<scope>test</scope>` (no version; managed by Spring Boot BOM).

**Create:**
- `src/test/resources/application-test.properties` — test profile:
  - `spring.flyway.enabled=false`
  - `spring.jpa.hibernate.ddl-auto=create-drop`
  - (fallback only, if H2 DDL fails on `@JdbcTypeCode(NAMED_ENUM)`):
    `spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`

**Do NOT touch:** `SocialAnalyticsApplicationTests.java`, any `src/main/**`, `V1__init_schema.sql`.

## Implementation Steps
1. Add H2 dependency block to `pom.xml` under `<dependencies>`, `<scope>test</scope>`, no explicit version.
2. Create `src/test/resources/application-test.properties` with Flyway disabled + `ddl-auto=create-drop`.
3. Verify resolution: `./mvnw -q dependency:list -Dscope=test | grep h2` returns the artifact.
4. Do NOT add `MODE=PostgreSQL` up front — add it in step-back only if Phase 02 D2-04 fails H2 DDL
   on the named-enum columns (see Risk).

## Todo List
- [x] D2-01: Add `com.h2database:h2` `<scope>test</scope>` to `pom.xml`
- [x] Create `src/test/resources/application-test.properties` (Flyway off, ddl-auto create-drop)
- [x] Confirm H2 resolves at test scope; confirm it is NOT in a `./mvnw package` artifact

## Success Criteria
- `./mvnw -q dependency:list -Dscope=test | grep h2` → H2 artifact listed (SC-001 dependency half).
- `./mvnw test` compiles (no test regressions introduced by the profile).
- H2 absent from production jar (`jar tf target/*.jar | grep -c h2database` → 0).

## Risk Assessment
| Risk | Likelihood | Impact | Countermove |
|------|-----------|--------|-------------|
| H2 rejects `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` DDL (Postgres-specific) | Medium | High (D2-04 can't boot) | Add `MODE=PostgreSQL` to the H2 URL in `application-test.properties`. No entity changes. |
| Test profile leaks into full-context `SocialAnalyticsApplicationTests` | Low | Medium | Profile file is only active under `@ActiveProfiles("test")` / slice tests; app test stays on default Postgres profile. Verify app test unchanged. |
| H2 accidentally shipped in prod jar | Low | Medium | Enforce `<scope>test</scope>`; verify with `jar tf`. |

## Rollback
Remove the H2 `<dependency>` block and delete `application-test.properties`. No production
impact — both are test-only artifacts.

## Next Steps
Phase 02 (test classes) depends on this phase. D2-04 in particular cannot run until H2 +
Flyway-off profile exist.

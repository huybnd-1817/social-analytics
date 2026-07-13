---
status: draft
authored_by: takumi
created: 2026-07-10
lang: en
---

# PostMetricTestSuite — Technical Spec

**Priority**: P1
**Type**: background
**Generated**: 2026-07-10

## Overview

This feature establishes the automated test infrastructure for the Social Analytics backend. It adds H2 as a test-scoped dependency and delivers three test layers — Mockito unit tests for `PostService` and `MetricService`, a `@DataJpaTest` repository slice for `PostRepository`, and `@WebMvcTest` MockMvc tests for `PostController` with `GlobalExceptionHandler`. No production code is created or changed; the scope is tests only (D2-01 through D2-05). The targeted surface is the paginated-list and soft-delete lifecycle of posts, plus the paginated-list of social metrics.

## Polymorphic Behavior

N/A — no discriminator fields in Key Entities.

(Note: `PostStatus` is an application-level enum with two values — `ACTIVE` and `DELETED` — but it is exercised in test assertions rather than driving render/persistence branching within this test-only feature. No DISC-### is applicable.)

## Cross-Cutting Logic

### Requirements

| Code | Description | Endpoint/Handler | Verifiable |
|------|-------------|------------------|------------|
| FR-001 | H2 in-memory DB available at test scope; Flyway disabled in JPA slice | `@DataJpaTest` / `application-test.properties` | yes |
| FR-002 | `PostService.findAll(Pageable)` returns only ACTIVE posts as a mapped `Page<PostResponse>` | Mockito unit test | yes |
| FR-003 | `PostService.deleteById(Long)` soft-deletes an ACTIVE post (status → DELETED, no DB row removal) | Mockito unit test | yes |
| FR-004 | `PostService.deleteById(Long)` throws `ResourceNotFoundException` when post is absent or already DELETED | Mockito unit test | yes |
| FR-005 | `MetricService.findAll(Pageable)` returns a mapped `Page<MetricResponse>` | Mockito unit test | yes |
| FR-006 | `PostRepository.findByStatus` and `findByIdAndStatus` work correctly against H2 | `@DataJpaTest` slice | yes |
| FR-007 | `GET /posts` returns 200 with a valid paginated JSON body | `@WebMvcTest` MockMvc | yes |
| FR-008 | `DELETE /posts/{id}` returns 204 on success | `@WebMvcTest` MockMvc | yes |
| FR-009 | `DELETE /posts/{id}` returns 404 with `{"error": "Post not found: {id}"}` when post missing | `@WebMvcTest` via `GlobalExceptionHandler` | yes |

### Business Rules

### BR-001_ActiveOnlyFilter
**Linked FR:** FR-002
**Source:** `src/main/java/com/sunasterisk/socialanalytics/service/PostService.java:21-24`
**Applies to:** `PostService.findAll`
**Rule:** Only posts with `PostStatus.ACTIVE` are surfaced. DELETED posts are invisible to read queries.

**Pseudocode:**
```text
findAll(pageable):
  return postRepository.findByStatus(ACTIVE, pageable).map(PostResponse::from)
```

### BR-002_SoftDeleteGuard
**Linked FR:** FR-003, FR-004
**Source:** `src/main/java/com/sunasterisk/socialanalytics/service/PostService.java:26-31`
**Applies to:** `PostService.deleteById`
**Rule:** Delete only succeeds if the target post exists AND is ACTIVE. Sets `status = DELETED`; the DB row is retained. Non-existent or already-DELETED posts raise `ResourceNotFoundException`.

**Pseudocode:**
```text
deleteById(id):
  post = postRepository.findByIdAndStatus(id, ACTIVE)
            .orElseThrow(() -> ResourceNotFoundException("Post not found: " + id))
  post.setStatus(DELETED)
  // transaction commit persists — no explicit save() due to @Transactional dirty-check
```

### BR-003_404Mapping
**Linked FR:** FR-009
**Source:** `src/main/java/com/sunasterisk/socialanalytics/controller/GlobalExceptionHandler.java:21-25`
**Applies to:** `GlobalExceptionHandler.handleNotFound`
**Rule:** `ResourceNotFoundException` is mapped to HTTP 404 with body `{"error": "<exception message>"}`.

**Pseudocode:**
```text
handleNotFound(ex):
  return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()))
```

### Decision Logic

N/A — no user-facing decision logic beyond DISC-### Polymorphic Behavior.

### State Machines

None.

### Algorithms

None.

### External Integrations

None.

### Verification

- **SC-001** — `PostServiceTest.findAll_returnsActivePosts_paged` passes; mock verifies `findByStatus(ACTIVE, pageable)` called once. (covers FR-001, FR-002, BR-001)
- **SC-002** — `PostServiceTest.deleteById_softDeletes_activePost` passes; captured post has `status == DELETED`. (covers FR-003, BR-002)
- **SC-003** — `PostServiceTest.deleteById_throwsResourceNotFoundException_whenNotFound` passes; `assertThrows(ResourceNotFoundException.class, ...)` succeeds. (covers FR-004, BR-002)
- **SC-004** — `MetricServiceTest.findAll_returnsMappedPage` passes; mapped `MetricResponse` fields match entity values. (covers FR-005)
- **SC-005** — `PostRepositoryTest` persists ACTIVE and DELETED posts; `findByStatus(ACTIVE, ...)` returns only ACTIVE; `findByIdAndStatus` returns present/absent correctly. (covers FR-006)
- **SC-006** — `PostControllerTest.list_returns200WithPageJson` passes; response contains `content` array. (covers FR-007)
- **SC-007** — `PostControllerTest.delete_returns204_whenFound` passes. (covers FR-008)
- **SC-008** — `PostControllerTest.delete_returns404_whenNotFound` passes; JSON body contains `"error"` key. (covers FR-009, BR-003)

---

**Client behavior:** see
[`behavior-logic.md`](../../docs/system/behavior-logic.md) (client-side patterns — debounce, optimistic UI, polling, upload, realtime),
[`permissions.md`](../../docs/system/permissions.md) (feature flags / experiments / env / locale gates),
[`architecture.md`](../../docs/system/architecture.md) (guards / deep-link state restoration / unsaved-changes protection).

## User Stories

### US001_H2TestDependency — Add H2 test dependency (Priority: P1)

**What happens:** A developer adds `com.h2database:h2` with `<scope>test</scope>` to `pom.xml` so that `@DataJpaTest` slices can boot an in-memory relational database without requiring a running PostgreSQL instance.
**Why this priority:** All JPA-slice tests (D2-04) depend on this. Without H2, `@DataJpaTest` cannot start.
**Independent Test:** Running `./mvnw dependency:list -Dscope=test | grep h2` returns the H2 artifact; `@DataJpaTest` context loads without a Postgres connection.

**Acceptance Scenarios:**

1. **Given** pom.xml has no H2 dependency, **When** the H2 test-scoped dependency is added, **Then** `./mvnw test` compiles and JPA-slice tests are able to load an in-memory datasource.
2. **Given** H2 is scope test, **When** the production JAR is built, **Then** H2 is not present in the packaged artifact.

**Requirements fulfilled:**
- **FR-001** H2 available at test scope, Flyway disabled in JPA slice — `@DataJpaTest` / `application-test.properties`

**Rules enforced:** None additional.

**Verification:**
- **SC-001** (see Cross-Cutting Logic)

---

### US002_PostServiceUnitTests — PostService Mockito unit tests (Priority: P1)

**What happens:** A developer authors `PostServiceTest` using `@ExtendWith(MockitoExtension.class)` with `@Mock PostRepository` and `@InjectMocks PostService`. Tests cover `findAll` (pagination + ACTIVE filter), `deleteById` success (soft delete), and `deleteById` not-found (exception). No create method is tested (user decision).
**Why this priority:** Core business invariants (ACTIVE-only reads, soft-delete guard, not-found exception) must be locked down before any integration test adds noise.
**Independent Test:** Run `./mvnw test -pl . -Dtest=PostServiceTest`; all three test methods pass without a database connection.

**Acceptance Scenarios:**

1. **Given** a `PostRepository` mock returning a page of ACTIVE posts, **When** `findAll(pageable)` is called, **Then** the returned page maps each `Post` to `PostResponse` and the mock received exactly one call to `findByStatus(ACTIVE, pageable)`.
2. **Given** `findByIdAndStatus` mock returns an ACTIVE post, **When** `deleteById(id)` is called, **Then** the post's status is set to `DELETED` and no `save()` is explicitly called (dirty-check under `@Transactional`).
3. **Given** `findByIdAndStatus` mock returns empty, **When** `deleteById(id)` is called, **Then** `ResourceNotFoundException` is thrown with message `"Post not found: {id}"`.

**Requirements fulfilled:**
- **FR-002** `PostService.findAll(Pageable)` returns only ACTIVE posts — `PostService::findAll`
  **Source:** `src/main/java/com/sunasterisk/socialanalytics/service/PostService.java:21-24`
- **FR-003** `PostService.deleteById(Long)` soft-deletes an ACTIVE post — `PostService::deleteById`
  **Source:** `src/main/java/com/sunasterisk/socialanalytics/service/PostService.java:26-31`
- **FR-004** `PostService.deleteById(Long)` throws `ResourceNotFoundException` when post absent — `PostService::deleteById`
  **Source:** `src/main/java/com/sunasterisk/socialanalytics/service/PostService.java:28-29`

**Rules enforced:** BR-001_ActiveOnlyFilter (see Cross-Cutting Logic), BR-002_SoftDeleteGuard (see Cross-Cutting Logic)

**Verification:**
- **SC-001**, **SC-002**, **SC-003** (see Cross-Cutting Logic)

---

### US003_MetricServiceUnitTests — MetricService Mockito unit tests (Priority: P2)

**What happens:** A developer authors `MetricServiceTest` covering `findAll(Pageable)` — verifying that `SocialMetricRepository.findAll(pageable)` is delegated and each result is mapped to a `MetricResponse`. No additional service methods are tested (user decision).
**Why this priority:** Lower than post tests; `MetricService` has less complex business logic (no soft-delete, no status filter).
**Independent Test:** Run `./mvnw test -Dtest=MetricServiceTest`; passes without a database connection.

**Acceptance Scenarios:**

1. **Given** a mock `SocialMetricRepository` returning a page of metrics, **When** `MetricService.findAll(pageable)` is called, **Then** the returned page contains `MetricResponse` records whose fields match the entity's values, and the mock received `findAll(pageable)` once.

**Requirements fulfilled:**
- **FR-005** `MetricService.findAll(Pageable)` returns a mapped page — `MetricService::findAll`
  **Source:** `src/main/java/com/sunasterisk/socialanalytics/service/MetricService.java:18-21`

**Rules enforced:** None.

**Verification:**
- **SC-004** (see Cross-Cutting Logic)

---

### US004_PostRepositorySliceTests — PostRepository @DataJpaTest slice (Priority: P1)

**What happens:** A developer authors `PostRepositoryTest` with `@DataJpaTest`, imports `JpaAuditingConfig`, disables Flyway (`spring.flyway.enabled=false`), and relies on Hibernate `create-drop` against H2. Tests cover `findByStatus` (returns only matching-status posts) and `findByIdAndStatus` (returns present when match, empty when absent or status mismatch).
**Why this priority:** Repository slice tests validate the Spring Data query derivation — the custom query methods that the service layer relies on.
**Independent Test:** Run `./mvnw test -Dtest=PostRepositoryTest`; H2 boots, schema is created by Hibernate, tests pass without Postgres.

**Acceptance Scenarios:**

1. **Given** two persisted posts (one ACTIVE, one DELETED), **When** `findByStatus(ACTIVE, pageable)` is called, **Then** only the ACTIVE post is returned.
2. **Given** an ACTIVE post with a known id, **When** `findByIdAndStatus(id, ACTIVE)` is called, **Then** a non-empty `Optional<Post>` is returned.
3. **Given** no post with id 999, **When** `findByIdAndStatus(999, ACTIVE)` is called, **Then** `Optional.empty()` is returned.
4. **Given** a DELETED post with a known id, **When** `findByIdAndStatus(id, ACTIVE)` is called, **Then** `Optional.empty()` is returned (status mismatch).

**Requirements fulfilled:**
- **FR-006** `PostRepository` query methods work correctly against H2 — `PostRepository`
  **Source:** `src/main/java/com/sunasterisk/socialanalytics/repository/PostRepository.java:13-17`

**Rules enforced:** BR-001_ActiveOnlyFilter (validates query derivation)

**Verification:**
- **SC-005** (see Cross-Cutting Logic)

---

### US005_PostControllerMvcTests — PostController @WebMvcTest MockMvc tests (Priority: P1)

**What happens:** A developer authors `PostControllerTest` with `@WebMvcTest(PostController.class)` and mocks `PostService`. Tests cover `GET /posts` returning 200 with a page JSON body, `DELETE /posts/{id}` returning 204, and `DELETE /posts/{missing-id}` triggering `GlobalExceptionHandler` to return 404 with `{"error": "..."}`.
**Why this priority:** Controller slice tests lock down the HTTP contract: status codes, response shape, and error-handler integration — without starting a full context.
**Independent Test:** Run `./mvnw test -Dtest=PostControllerTest`; passes without Postgres or H2.

**Acceptance Scenarios:**

1. **Given** `postService.findAll()` mock returns an empty `PageImpl`, **When** `GET /posts` is performed, **Then** response status is 200 and body contains `"content"` field.
2. **Given** `postService.deleteById(id)` mock does nothing, **When** `DELETE /posts/1` is performed, **Then** response status is 204.
3. **Given** `postService.deleteById(id)` mock throws `ResourceNotFoundException("Post not found: 99")`, **When** `DELETE /posts/99` is performed, **Then** response status is 404 and JSON body is `{"error": "Post not found: 99"}`.

**Requirements fulfilled:**
- **FR-007** `GET /posts` returns 200 with page JSON — `PostController::list`
  **Source:** `src/main/java/com/sunasterisk/socialanalytics/controller/PostController.java:24-30`
- **FR-008** `DELETE /posts/{id}` returns 204 — `PostController::delete`
  **Source:** `src/main/java/com/sunasterisk/socialanalytics/controller/PostController.java:32-37`
- **FR-009** 404 on missing post via `GlobalExceptionHandler` — `GlobalExceptionHandler::handleNotFound`
  **Source:** `src/main/java/com/sunasterisk/socialanalytics/controller/GlobalExceptionHandler.java:21-25`

**Rules enforced:** BR-003_404Mapping (see Cross-Cutting Logic)

**Verification:**
- **SC-006**, **SC-007**, **SC-008** (see Cross-Cutting Logic)

---

### Edge Cases

See edge-cases.md.

## Key Entities

| Entity | Table | Key Columns | Purpose |
|--------|-------|-------------|---------|
| `Post` | `posts` | `id`, `status` (PostStatus enum), `user_id`, `platform`, `platformPostId`, `createdAt` | Primary entity under test — findAll + soft-delete paths |
| `SocialMetric` | `social_metrics` | `id`, `post_id`, `likesCount`, `sharesCount`, `commentsCount`, `crawledAt`, `createdAt` | Entity under MetricService test — findAll mapping |
| `PostStatus` | n/a (enum) | `ACTIVE`, `DELETED` | Discriminates readable vs. deleted posts in repository queries |

## Artifact References

| Artifact | File | Codes Used | Reviewed |
|----------|------|------------|----------|
| Feature List | `plans/260710-2030-d2-unit-tests/spec/feature-list.md` | TBD (draft) | [ ] |
| API Map | TBD (draft) | TBD (draft) | [ ] |
| Entities | TBD (draft) | TBD (draft) | [ ] |
| Screens | N/A — backend-only feature | — | — |
| Behavior Logic | TBD (draft) | TBD (draft) | [ ] |
| Permissions Matrix | TBD (draft) | TBD (draft) | [ ] |
| User Stories | TBD (draft) | US001, US002, US003, US004, US005 | [ ] |

## Assumptions

- `Post.user` is `nullable = false` at the DB level (confirmed from entity). Test data for `@DataJpaTest` must persist a `User` row first, or use a nullable workaround by setting user via `@ManyToOne` with a saved entity.
- `JpaAuditingConfig` must be imported in `@DataJpaTest` via `@Import(JpaAuditingConfig.class)` to populate `createdAt`/`updatedAt`; without it, auditing fields remain null and assertions on them will fail.
- Flyway is disabled in the JPA test slice (`spring.flyway.enabled=false` in `application-test.properties` or via `@DataJpaTest(properties = "spring.flyway.enabled=false")`); Hibernate `create-drop` generates the schema from entity mappings.
- H2 dialect handles `Instant` columns (mapped as `TIMESTAMP`) and `EnumType.STRING` without custom type registration; `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` may require a dialect override or mapping adjustment when targeting H2 (NAMED_ENUM is PostgreSQL-specific).
- `@WebMvcTest` does not load `JpaAuditingConfig` because `@EnableJpaAuditing` is correctly isolated in its own `@Configuration` class (existing design decision, confirmed in source).
- The `SocialAnalyticsApplicationTests` full-context test requires a live Postgres; it is out of scope for D2 and should not be modified.

## Source Code References

No test source files exist yet — this spec drives their creation. The following production classes are the subjects under test (line ranges confirmed by Read):

| Symbol | Path | Purpose |
|--------|------|---------|
| `PostService` | `src/main/java/com/sunasterisk/socialanalytics/service/PostService.java:1-32` | Under test for D2-02: `findAll`, `deleteById` |
| `MetricService` | `src/main/java/com/sunasterisk/socialanalytics/service/MetricService.java:1-22` | Under test for D2-03: `findAll` |
| `PostRepository` | `src/main/java/com/sunasterisk/socialanalytics/repository/PostRepository.java:1-17` | Under test for D2-04: `findByStatus`, `findByIdAndStatus` |
| `PostController` | `src/main/java/com/sunasterisk/socialanalytics/controller/PostController.java:1-38` | Under test for D2-05: `list`, `delete` |
| `GlobalExceptionHandler` | `src/main/java/com/sunasterisk/socialanalytics/controller/GlobalExceptionHandler.java:1-52` | Under test for D2-05: 404 mapping |
| `JpaAuditingConfig` | `src/main/java/com/sunasterisk/socialanalytics/config/JpaAuditingConfig.java:1-14` | Must be imported in `@DataJpaTest` |
| `ResourceNotFoundException` | `src/main/java/com/sunasterisk/socialanalytics/util/ResourceNotFoundException.java:1-7` | Expected exception in deleteById not-found scenario |

## Unresolved Questions

All gaps were resolved with the user during clarification (2026-07-10):

1. **H2 + NAMED_ENUM compatibility** — RESOLVED: use H2 as-is with Hibernate `create-drop` (Hibernate 7 H2 dialect supports named-enum DDL on H2 2.2+); if DDL fails at runtime, add H2 PostgreSQL compatibility mode (`MODE=PostgreSQL`) in test properties. No production entity changes.
2. **User NOT NULL for test data** — RESOLVED: persist a real `User` in `@BeforeEach` via `TestEntityManager`; posts reference it. No entity changes.
3. **`SocialAnalyticsApplicationTests` Postgres dependency** — RESOLVED: leave untouched (out of D2 scope). It passes when local Postgres is up, which matches the dev workflow.

# Phase 02 — Test Classes (D2-02..D2-05)

## Context Links
- Spec: `spec/post-metric-test-suite/technical-spec.md` (FR-002..009, BR-001..003, SC-001..008, US002..005)
- Edge cases: `spec/post-metric-test-suite/edge-cases.md`
- Scout: `plans/reports/researcher-260710-2030-d2-test-scout.md`

## Overview
- **Priority:** P1
- **Status:** completed
- **Goal:** Author 4 test classes across 3 layers proving the post/metric read + soft-delete paths.
- **Depends on:** Phase 01 (H2 + Flyway-off profile — required for D2-04).

## Requirements
- **FR-002/003/004** PostService findAll / soft-delete / not-found → `PostServiceTest`
- **FR-005** MetricService findAll mapping → `MetricServiceTest`
- **FR-006** PostRepository query derivation → `PostRepositoryTest`
- **FR-007/008/009** PostController HTTP contract + 404 mapping → `PostControllerTest`

## Grounded Facts (verified from source — must respect)
- `PostService.deleteById` uses `@Transactional` **dirty-check**, NO explicit `save()`
  (PostService.java:26-31). → assert `post.getStatus() == DELETED`; `verify(repo, never()).save(...)`.
- `PostResponse` / `MetricResponse` are records with a static `from(entity)`; `MetricResponse.from`
  dereferences `metric.getPost().getId()` → the mock `SocialMetric` MUST carry a `Post` with an id.
- `Post.user` is `nullable=false`; `User` requires `email` (unique) + `name`. `@DataJpaTest` must
  persist a real `User` first via `TestEntityManager` in `@BeforeEach`.
- `@DataJpaTest` needs `@Import(JpaAuditingConfig.class)` for `@CreatedDate`/`@LastModifiedDate`.
- `@WebMvcTest` does NOT load `JpaAuditingConfig` (isolated `@EnableJpaAuditing`) — no metamodel error.
- Spring Boot 4.x: `@MockitoBean` (the `@MockBean` was removed).
- Controller `GET /posts` returns raw `Page<PostResponse>` (page serialization `via_dto` → stable `content` array).
- 404 body shape: `{"error": "Post not found: {id}"}` (GlobalExceptionHandler.java:21-25).

## Related Code Files
**Create (mirror main packages under `src/test/java/com/sunasterisk/socialanalytics/`):**
- `service/PostServiceTest.java` — `@ExtendWith(MockitoExtension.class)`, `@Mock PostRepository`, `@InjectMocks PostService`
- `service/MetricServiceTest.java` — `@ExtendWith(MockitoExtension.class)`, `@Mock SocialMetricRepository`, `@InjectMocks MetricService`
- `repository/PostRepositoryTest.java` — `@DataJpaTest`, `@ActiveProfiles("test")`, `@Import(JpaAuditingConfig.class)`, `TestEntityManager`
- `controller/PostControllerTest.java` — `@WebMvcTest(PostController.class)`, `MockMvc`, `@MockitoBean PostService`

**Read for context:** the 7 source classes in the scout's Source Code References table.
**Do NOT modify:** any `src/main/**`, `SocialAnalyticsApplicationTests.java`.

## Data Flow (per class)
- **PostServiceTest:** mock returns `PageImpl<Post>` → service maps → assert `PostResponse` fields +
  `verify(findByStatus(ACTIVE, pageable))` once. Delete: mock `findByIdAndStatus` returns
  `Optional.of(activePost)` → assert status flips to DELETED; empty `Optional` → `assertThrows`.
- **MetricServiceTest:** mock `findAll(pageable)` returns `PageImpl<SocialMetric>` (metric has a Post w/ id)
  → assert mapped `MetricResponse` fields match + `verify(findAll)` once.
- **PostRepositoryTest:** `@BeforeEach` persist User → persist 1 ACTIVE + 1 DELETED Post via
  `TestEntityManager` → exercise `findByStatus` / `findByIdAndStatus`.
- **PostControllerTest:** stub `postService.findAll(...)` → empty `PageImpl` → GET asserts 200 + `$.content`.
  Stub `deleteById` (doNothing / throw) → DELETE asserts 204 / 404 + `$.error`.

## Implementation Steps
1. `PostServiceTest`: build fixture Posts (Builder) with ACTIVE status + a `User`; wrap in `PageImpl`.
   Write the 3 methods (SC-001..003). For delete-success use `ArgumentCaptor<Post>` or direct fixture ref;
   assert `never().save(...)` to lock the dirty-check contract.
2. `MetricServiceTest`: build a `SocialMetric` (Builder) referencing a `Post` with a set id; `PageImpl`;
   write findAll mapping method (SC-004) asserting every mapped field + zero-count safety (edge row 8).
3. `PostRepositoryTest`: `@BeforeEach` persist a valid `User`; persist ACTIVE + DELETED Posts.
   Write findByStatus + findByIdAndStatus present/absent/status-mismatch methods (SC-005).
4. `PostControllerTest`: `@WebMvcTest(PostController.class)`, `@MockitoBean PostService`.
   Write list-200, delete-204, delete-404 methods (SC-006..008).
5. Run `./mvnw test`; if D2-04 fails H2 DDL on named-enum columns → apply Phase 01 `MODE=PostgreSQL` fallback.

## Todo List (exact spec method names)
- [x] `PostServiceTest.findAll_returnsActivePosts_paged` (SC-001, FR-002, BR-001)
- [x] `PostServiceTest.deleteById_softDeletes_activePost` (SC-002, FR-003, BR-002)
- [x] `PostServiceTest.deleteById_throwsResourceNotFoundException_whenNotFound` (SC-003, FR-004, BR-002)
- [x] `MetricServiceTest.findAll_returnsMappedPage` (SC-004, FR-005)
- [x] `PostRepositoryTest` — findByStatus ACTIVE-only + findByIdAndStatus present/absent/mismatch (SC-005, FR-006)
- [x] `PostControllerTest.list_returns200WithPageJson` (SC-006, FR-007)
- [x] `PostControllerTest.delete_returns204_whenFound` (SC-007, FR-008)
- [x] `PostControllerTest.delete_returns404_whenNotFound` (SC-008, FR-009, BR-003)

## Test Matrix
| Layer | Class | Proves |
|-------|-------|--------|
| Unit (Mockito) | PostServiceTest, MetricServiceTest | business logic: ACTIVE filter, soft-delete guard, mapping |
| Slice (@DataJpaTest) | PostRepositoryTest | Spring Data query derivation on real (H2) DB |
| Slice (@WebMvcTest) | PostControllerTest | HTTP contract: status codes, page JSON, error-handler wiring |

## Success Criteria
- `./mvnw test` → all 8 methods green, no Postgres required for the new classes.
- `PostServiceTest` asserts `never().save()` (dirty-check contract locked).
- `PostRepositoryTest` returns only ACTIVE from `findByStatus`; `findByIdAndStatus` empty on absent + DELETED.
- `PostControllerTest` 404 body is exactly `{"error":"Post not found: {id}"}`.

## Risk Assessment
| Risk | Likelihood | Impact | Countermove |
|------|-----------|--------|-------------|
| H2 DDL fails on `@JdbcTypeCode(NAMED_ENUM)` (Post.status/platform, User.role) | Medium | High | Phase 01 `MODE=PostgreSQL` fallback; no entity edits. |
| `MetricResponse.from` NPE — mock metric lacks a Post | Medium | Low | Fixture builds `SocialMetric` with a `Post` carrying an id. |
| `@WebMvcTest` pulls JpaAuditingConfig → "metamodel empty" | Low | Medium | Config is isolated by design (verified); scope test to `PostController.class`. |
| `@DataJpaTest` Post insert violates user_id NOT NULL | Medium | Medium | Persist User first in `@BeforeEach` (edge row 5). |
| `@InjectMocks` mismatch if constructor changes | Low | Low | Tests are read-only on main; scoped to current constructors. |

## Rollback
Delete the 4 new test files. No production impact — additive test-only files.

## Next Steps
Final: hand to `tester` agent to run `./mvnw test`; on failure apply the Phase 01 H2 fallback and re-run.

---
status: draft
authored_by: takumi
created: 2026-07-10
lang: en
---

# PostMetricTestSuite — Edge Cases

| Scenario | What Happens | User-Facing Message |
|----------|--------------|---------------------|
| `deleteById` called with an id that does not exist in the database | `PostService` calls `findByIdAndStatus(id, ACTIVE)`, receives `Optional.empty()`, throws `ResourceNotFoundException`; `GlobalExceptionHandler` maps it to a 404 response | "Post not found: {id}" |
| `deleteById` called with an id of a post already soft-deleted (status = DELETED) | `findByIdAndStatus(id, ACTIVE)` returns `Optional.empty()` because the status filter excludes DELETED records; same 404 path as non-existent post | "Post not found: {id}" |
| `findAll` called when no ACTIVE posts exist (empty repository or all DELETED) | `postRepository.findByStatus(ACTIVE, pageable)` returns an empty `Page`; service maps and returns an empty page with zero content and valid pagination metadata | None — empty list with page metadata returned (no error) |
| `@DataJpaTest` slice starts with Flyway enabled (misconfiguration) | Flyway attempts to run `V1__init_schema.sql` against H2; PostgreSQL-specific DDL (NAMED_ENUM type, TIMESTAMPTZ) causes a SQL syntax error and the test context fails to load | None user-facing — test suite fails to start with a Flyway DDL error in CI logs |
| `@DataJpaTest` test persists a `Post` without a required `User` (user_id NOT NULL) | H2 enforces the NOT NULL constraint; `DataIntegrityViolationException` is thrown from the JPA flush, causing the test to fail with a constraint violation | None user-facing — test fails with a constraint message in the test output |
| `@WebMvcTest` slice loads `JpaAuditingConfig` indirectly through the main application class | Because `@EnableJpaAuditing` is isolated in `JpaAuditingConfig` (not on `@SpringBootApplication`), the slice does not trigger JPA metamodel loading and starts cleanly | None — normal test startup |
| `GET /posts` receives an invalid sort field (e.g., `?sort=nonexistent,asc`) | `PropertyReferenceException` is thrown by Spring Data; `GlobalExceptionHandler` maps it to 400 with `{"error": "Invalid sort/filter property: nonexistent"}` | "Invalid sort/filter property: nonexistent" |
| MetricResponse mapping called on a SocialMetric with all-zero counts | All numeric fields (`likesCount`, `sharesCount`, etc.) are zero; mapping succeeds and `MetricResponse` reflects zeros — no NPE or exception | None — valid response with zero-count metrics |

# Codebase Study — Excel Import/Export (D2-06..D2-11)

**Date:** 2026-07-13  
**Branch:** feature/export-import-excel  
**Scope:** pom.xml, properties, all entities, repos, services, controllers, tests, DB design

---

## 1. Stack

| Item | Value |
|---|---|
| Java | 26 |
| Spring Boot | 4.1.0 |
| Spring Data JPA | via Boot starter |
| springdoc-openapi | 3.0.3 |
| DB | PostgreSQL (Flyway migrations) |
| Test slice deps | `spring-boot-data-jpa-test`, `spring-boot-webmvc-test` (Boot 4.x split) |

**Apache POI: NOT present.** Must add in D2-06.  
**Multipart config: NOT present** in any properties file. Spring Boot default: 1 MB max-file-size, 10 MB max-request-size. For Excel import this is too tight; must set `spring.servlet.multipart.max-file-size` and `spring.servlet.multipart.max-request-size` before wiring the controller.

---

## 2. Entity Field Reference

### `Post` (table `posts`) — extends `BaseEntity`

| Field (Java) | Column | Type | Nullable |
|---|---|---|---|
| id | id | Long / BIGSERIAL | PK |
| **user** | user_id | ManyToOne→User | NOT NULL |
| platform | platform | SocialProvider (NAMED_ENUM) | NOT NULL |
| platformPostId | platform_post_id | VARCHAR(255) | NOT NULL |
| title | title | VARCHAR(500) | nullable |
| content | content | TEXT | nullable |
| postUrl | post_url | TEXT | nullable |
| publishedAt | published_at | TIMESTAMPTZ | nullable |
| **importBatch** | import_batch_id | ManyToOne→ImportBatch | nullable |
| status | status | PostStatus (NAMED_ENUM) | NOT NULL, default ACTIVE |
| createdAt | created_at | TIMESTAMPTZ | NOT NULL (via BaseEntity @CreatedDate) |
| updatedAt | updated_at | TIMESTAMPTZ | NOT NULL (via BaseEntity @LastModifiedDate) |

**Unique constraint (partial index):** `(platform, platform_post_id) WHERE status = 'ACTIVE'`  — re-import of a deleted post's key is allowed.

Enums: `SocialProvider` = {FACEBOOK, TWITTER}; `PostStatus` = {ACTIVE, DELETED}

### `ImportBatch` (table `import_batches`) — NOT extending BaseEntity

| Field (Java) | Column | Type | Nullable |
|---|---|---|---|
| id | id | Long | PK |
| **user** | user_id | ManyToOne→User | NOT NULL |
| fileName | file_name | VARCHAR(255) | NOT NULL |
| totalRecords | total_records | Integer | NOT NULL, default 0 |
| successRecords | success_records | Integer | NOT NULL, default 0 |
| failedRecords | failed_records | Integer | NOT NULL, default 0 |
| status | status | ImportBatchStatus (NAMED_ENUM) | NOT NULL, default PENDING |
| importedAt | imported_at | TIMESTAMPTZ | NOT NULL (@CreatedDate on entity) |

Enums: `ImportBatchStatus` = {PENDING, PROCESSING, DONE, FAILED}  
**No createdAt/updatedAt** (no BaseEntity). `importedAt` filled via `@CreatedDate`; comment says service will overwrite it on completion.

### `SocialMetric` (table `social_metrics`) — NOT extending BaseEntity

| Field (Java) | Column | Type | Nullable |
|---|---|---|---|
| id | id | Long | PK |
| **post** | post_id | ManyToOne→Post | NOT NULL |
| likesCount | likes_count | Long | NOT NULL, default 0 |
| sharesCount | shares_count | Long | NOT NULL, default 0 |
| commentsCount | comments_count | Long | NOT NULL, default 0 |
| followersCount | followers_count | Long | NOT NULL, default 0 |
| reach | reach | Long | NOT NULL, default 0 |
| impressions | impressions | Long | NOT NULL, default 0 |
| crawledAt | crawled_at | TIMESTAMPTZ | NOT NULL |
| createdAt | created_at | TIMESTAMPTZ | NOT NULL (@CreatedDate) |

### `BaseEntity`
Provides `createdAt` (@CreatedDate, updatable=false) and `updatedAt` (@LastModifiedDate). Both use `Instant`.

### `User` (relevant to import)
Fields: id, email (NOT NULL, UNIQUE), name (NOT NULL), avatarUrl (nullable), role (UserRole, default ADMIN).  
`Post.user` and `ImportBatch.user` are both `nullable = false` FK → **import must resolve a real User row**.

---

## 3. Repositories

| Repo | Relevant methods |
|---|---|
| `PostRepository` | `findByStatus(PostStatus, Pageable)`, `findByIdAndStatus(Long, PostStatus)` |
| `ImportBatchRepository` | none beyond JpaRepository |
| `SocialMetricRepository` | **`findTop1ByPostOrderByCrawledAtDesc(Post)`** → `Optional<SocialMetric>` |

**"Latest metric per post" query EXISTS** (`findTop1ByPostOrderByCrawledAtDesc`). Export service can use it directly.  
**Gap:** no bulk "latest metric per each of N posts" query. Export for many posts will issue N+1 queries unless a custom `@Query` or a JOIN is added. For D2-10 (stream export) this is a risk.

---

## 4. Services

Both services use `@RequiredArgsConstructor` (Lombok constructor injection) and `@Transactional` / `@Transactional(readOnly = true)`.

`PostService.deleteById` throws `ResourceNotFoundException` (in `util/`) which `GlobalExceptionHandler` maps to HTTP 404 `{"error": "…"}`.

`MetricService.findAll` — plain paginated read, no `findLatestByPostId` method yet (exists only on repository, not surfaced in service).

**No existing `ImportBatchService`** — must be created.

---

## 5. Controllers & Response Conventions

| Aspect | Convention |
|---|---|
| Base path | `/posts`, `/metrics` |
| Pagination | `@PageableDefault(size=20, sort="createdAt", direction=DESC)` + `@ParameterObject` |
| List response | `Page<XxxResponse>` returned directly (serialized via `spring.data.web.pageable.serialization-mode=via_dto`) |
| Delete | `ResponseEntity<Void>` + `204 No Content` |
| Error shape | `Map<String, String>` — key `"error"`, value message string |
| Swagger | `@Tag(name=…)` on class, `@Operation(summary=…)` on methods |
| DTOs | Java `record` types with static `from(Entity)` factory |

`GlobalExceptionHandler` handles:
- `ResourceNotFoundException` → 404
- `PropertyReferenceException` → 400
- `InvalidDataAccessApiUsageException` → 400
- `DataIntegrityViolationException` → 409

**No handler for generic `IllegalArgumentException` or `IOException`** — import errors (bad file format, bad cell value) need a handler added or caught in service and wrapped.

---

## 6. Test Conventions

| Aspect | Pattern |
|---|---|
| Service tests | `@ExtendWith(MockitoExtension.class)`, `@Mock` + `@InjectMocks`, no Spring context |
| Controller tests | `@WebMvcTest(XxxController.class)` from `spring-boot-webmvc-test`, `@MockitoBean` (Boot 4.x), `@ActiveProfiles("test")` |
| Assertion lib | AssertJ (`assertThat`) + JUnit5 `assertThrows` |
| Mock framework | Mockito `when/verify/doThrow` |
| Fixture style | Private helper method returning entity built with Lombok `@Builder` |
| Naming | `methodName_condition_expectedOutcome` |
| Notable | `@CreatedDate` does NOT fire outside Spring context; tests assert `createdAt == null` explicitly |

`ExcelImportServiceTest` (D2-11) should follow same pattern: `@ExtendWith(MockitoExtension.class)`, mock repos + `ImportBatchRepository`, builder fixtures, AssertJ assertions, three cases: valid file / missing columns / empty file.

---

## 7. DB Design Confirmations (V1__init_schema.sql)

- `import_batches` columns confirmed: `user_id NOT NULL FK`, `file_name NOT NULL`, `total_records/success_records/failed_records INT NOT NULL DEFAULT 0`, `status import_batch_status NOT NULL DEFAULT 'PENDING'`, `imported_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `posts` unique index is **partial**: `WHERE status = 'ACTIVE'` — re-import after soft-delete is safe
- `social_metrics` has composite index `(post_id, crawled_at DESC)` — supports latest-metric query efficiently
- No `updated_at` trigger on `import_batches` or `social_metrics` (none needed — they are append/update-once)

---

## 8. Gaps & Risks

| # | Gap/Risk | Impact | Recommendation |
|---|---|---|---|
| 1 | **Apache POI absent** | Build fails | Add `poi-ooxml` 5.x to pom.xml (D2-06) |
| 2 | **No multipart size limits** | Spring default 1MB blocks real Excel files | Add `spring.servlet.multipart.max-file-size=10MB` + `max-request-size=11MB` in `application.properties` (or dev profile) |
| 3 | **`Post.user` NOT NULL** — import must supply a real `User` | INSERT fails without valid user_id | Import endpoint needs authenticated user context OR a seed/system user; pre-OAuth2, simplest approach: accept `userId` path/param or hardcode seed user id=1 for D2 |
| 4 | **N+1 on export** — `findTop1ByPostOrderByCrawledAtDesc` runs per post | Performance on large exports | Add `SocialMetricRepository.findLatestPerPost(List<Long> postIds)` with `@Query` (DISTINCT ON or ROW_NUMBER), or accept N+1 for D2 demo scale |
| 5 | **No `IllegalArgumentException`/parse error handler** in `GlobalExceptionHandler` | Excel parse errors → 500 to client | Add handler for `IllegalArgumentException` → 400, or wrap in service and throw a typed exception |
| 6 | **`ImportBatch.user` NOT NULL** — same user resolution issue as Post | INSERT fails | Same as risk #3 |
| 7 | **`H2` + `NAMED_ENUM`** — `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` not supported by H2 | `ExcelImportServiceTest` will fail if it uses `@DataJpaTest` | Use pure Mockito (no Spring context) for `ExcelImportServiceTest` — consistent with existing service test pattern |
| 8 | **`PostResponse` excludes `user` field** — export needs user info? | Export may need user/author column | Decide whether export sheet includes user name; if yes, Post entity lazy-loads user — must fetch eagerly in export query |

---

## 9. POI Dependency to Add (D2-06)

```xml
<!-- Apache POI — Excel read/write (.xlsx = OOXML) -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>
```

`poi-ooxml` transitively brings `poi` (core). No need to declare `poi` separately. Latest stable as of mid-2025 is 5.3.x. Use `XSSFWorkbook` for `.xlsx`; avoid `HSSFWorkbook` (`.xls` only).

---

**Status:** DONE

**Unresolved questions:**
1. Which `User` should own the `ImportBatch` / `Post.user` FK before OAuth2 lands (D3)? Seed user id=1, or pass userId as request param?
2. Should export sheet include user/author name (requires eager fetch of `Post.user`)?
3. Accept N+1 for D2 export (demo scale) or add bulk latest-metric query now?
4. Multipart size limits — configure in base `application.properties` or dev-only profile?

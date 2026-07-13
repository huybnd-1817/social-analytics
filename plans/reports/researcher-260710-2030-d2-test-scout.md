# Scout Report — D2 Unit Test Targets (260710)

Scope: D2-01..D2-05 (test deps + unit/slice tests). User-confirmed: TESTS ONLY, no production code changes. D2-02 covers existing methods only (no create). D2-03 covers findAll only.

## Build
- `pom.xml`: Java 26, Spring Boot 4.1.0, Maven wrapper (`./mvnw`).
- `spring-boot-starter-test` present (JUnit5, Mockito, AssertJ, MockMvc, JSONassert). H2 NOT present → D2-01 = add `com.h2database:h2` scope test only.
- Flyway + PostgreSQL driver present. Flyway migration `V1__init_schema.sql` is Postgres SQL — for `@DataJpaTest` on H2, disable Flyway in test profile and rely on Hibernate `create-drop` from entities.

## Code under test (base pkg `com.sunasterisk.socialanalytics`)
- `service/PostService`: deps `PostRepository`. Methods: `Page<PostResponse> findAll(Pageable)` → `postRepository.findByStatus(PostStatus.ACTIVE, pageable)` + map; `void deleteById(Long)` → `findByIdAndStatus(id, ACTIVE)` orElseThrow `ResourceNotFoundException`, then soft delete (status→DELETED, save).
- `service/MetricService`: deps `SocialMetricRepository`. Method: `Page<MetricResponse> findAll(Pageable)` → `findAll(pageable)` + map.
- `repository/PostRepository`: `findByStatus(PostStatus, Pageable)`, `findByIdAndStatus(Long, PostStatus)`.
- `repository/SocialMetricRepository`: `findTop1ByPostOrderByCrawledAtDesc(Post)` (not in D2 scope — service never exposes it).
- `controller/PostController` (`/posts`): `GET` → `Page<PostResponse>`, `@PageableDefault(size=20, sort="createdAt", DESC)`; `DELETE /{id}` → 204.
- `controller/GlobalExceptionHandler` (@RestControllerAdvice): `ResourceNotFoundException`→404 `{"error":...}`, `PropertyReferenceException`→400, `InvalidDataAccessApiUsageException`→400, `DataIntegrityViolationException`→409.
- `util/ResourceNotFoundException`.
- DTOs (records): `PostResponse{id, platform, platformPostId, title, content, postUrl, publishedAt, status, createdAt}` + `from(Post)`; `MetricResponse{id, postId, likes/shares/comments/followersCount, reach, impressions, crawledAt, createdAt}` + `from(SocialMetric)`.
- Entities: `Post` extends `BaseEntity` (user @ManyToOne, platform enum SocialProvider, platformPostId, title, content, postUrl, publishedAt, importBatch @ManyToOne, status PostStatus default ACTIVE). `SocialMetric` standalone + `@EntityListeners(AuditingEntityListener)`, `@CreatedDate createdAt`. `BaseEntity` @MappedSuperclass with @CreatedDate/@LastModifiedDate (Instant).

## Test-slice considerations
- `config/JpaAuditingConfig` holds `@EnableJpaAuditing` separately (safe for @WebMvcTest); `@DataJpaTest` needs `@Import(JpaAuditingConfig.class)` for auditing fields.
- `spring.jpa.open-in-view=false`; `spring.data.web.pageable.max-page-size=100`; page serialization `via_dto` (stable Page JSON).
- Existing tests: only `SocialAnalyticsApplicationTests` (@SpringBootTest contextLoads) — requires Postgres running; no test properties file exists.
- Post.user is nullable in practice for tests? — verify in entity; test data must satisfy NOT NULL columns from entities.

## Unresolved questions
- H2 vs Postgres dialect differences for enum/Instant columns — expect fine with Hibernate DDL.
- `SocialAnalyticsApplicationTests` needs a running Postgres; decide whether to give it a test profile or leave untouched.

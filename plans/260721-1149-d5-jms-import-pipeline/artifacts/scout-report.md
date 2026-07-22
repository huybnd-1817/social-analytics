# Workshop Survey Report

## Project Summary

- **Name:** social-analytics
- **Stack:** Spring Boot 4.1.0 · Java 26 · Spring Framework 7 · PostgreSQL · Thymeleaf · ActiveMQ Artemis (embedded JMS)
- **Pattern:** MVC (Controller → Service → Repository → JPA) + Security Filter Chain + JMS async pipeline
- **Build tool:** Maven (pom.xml)
- **Auth:** Spring Security 7 · OAuth2 authorization_code (Facebook, Twitter/X)
- **Deployment:** Docker-ready; profiles: dev (H2-less Postgres), prod; test (H2 in-memory)
- **API style:** REST (JSON) + Thymeleaf server-rendered views (login, dashboard)
- **HEAD SHA:** 2d840b4966a0236c9bdf9e432093e3def550b6d7

---

## Relevant Files

### Entry Point
- `src/main/java/com/sunasterisk/socialanalytics/SocialAnalyticsApplication.java` — Spring Boot main class

### Configuration
- `src/main/java/com/sunasterisk/socialanalytics/config/AsyncConfig.java` — Thread pool config for @Async crawl jobs
- `src/main/java/com/sunasterisk/socialanalytics/config/JmsConfig.java` — MappingJackson2MessageConverter bean (JSON/TextMessage for JMS)
- `src/main/java/com/sunasterisk/socialanalytics/config/JpaAuditingConfig.java` — JPA auditing (created_at/updated_at)
- `src/main/java/com/sunasterisk/socialanalytics/config/OpenApiConfig.java` — Swagger/OpenAPI 3 setup (springdoc)
- `src/main/java/com/sunasterisk/socialanalytics/config/SecurityConfig.java` — Spring Security filter chain; OAuth2 login; CSRF; route permits
- `src/main/resources/application.properties` — Base config (Artemis JMS, datasource, JPA, Flyway, multipart, OAuth2)
- `src/main/resources/application-dev.properties` — Dev overrides
- `src/main/resources/application-prod.properties` — Prod overrides

### Controllers
- `src/main/java/com/sunasterisk/socialanalytics/controller/DashboardController.java` — GET / (Thymeleaf dashboard view)
- `src/main/java/com/sunasterisk/socialanalytics/controller/ExcelExportController.java` — GET /export-report (download .xlsx)
- `src/main/java/com/sunasterisk/socialanalytics/controller/ImportController.java` — POST /import-posts (multipart .xlsx upload)
- `src/main/java/com/sunasterisk/socialanalytics/controller/LoginController.java` — GET /login (Thymeleaf login view)
- `src/main/java/com/sunasterisk/socialanalytics/controller/MetricController.java` — GET /metrics, GET /metrics/last-updated
- `src/main/java/com/sunasterisk/socialanalytics/controller/PostController.java` — GET /posts (paginated), DELETE /posts/{id}
- `src/main/java/com/sunasterisk/socialanalytics/controller/GlobalExceptionHandler.java` — @RestControllerAdvice; handles ResourceNotFoundException + validation

### Services
- `src/main/java/com/sunasterisk/socialanalytics/service/ExcelImportService.java` — Validates + persists Excel rows; publishes ImportSucceededEvent after commit
- `src/main/java/com/sunasterisk/socialanalytics/service/ExcelExportService.java` — Streams ACTIVE posts + latest metrics as .xlsx
- `src/main/java/com/sunasterisk/socialanalytics/service/ImportBatchService.java` — CRUD for ImportBatch records
- `src/main/java/com/sunasterisk/socialanalytics/service/PostService.java` — Page<Post> list, soft-delete (status=DELETED)
- `src/main/java/com/sunasterisk/socialanalytics/service/MetricService.java` — Aggregated metrics query
- `src/main/java/com/sunasterisk/socialanalytics/service/CrawlJobService.java` — @Scheduled crawl orchestrator (delegating to SocialCrawlerService via @Async)
- `src/main/java/com/sunasterisk/socialanalytics/service/SocialCrawlerService.java` — @Async per-account crawler; fetches metrics from social APIs; writes SocialMetric

### Messaging (JMS — D5)
- `src/main/java/com/sunasterisk/socialanalytics/messaging/JmsQueues.java` — Queue name constants (IMPORT_COMPLETED)
- `src/main/java/com/sunasterisk/socialanalytics/messaging/ImportCompletedMessage.java` — Record DTO: batchId + recordCount (JSON over JMS)
- `src/main/java/com/sunasterisk/socialanalytics/messaging/ImportSucceededEvent.java` — Spring application event (batchId + recordCount); triggers JMS publish after DB commit
- `src/main/java/com/sunasterisk/socialanalytics/messaging/ImportEventProducer.java` — @TransactionalEventListener(AFTER_COMMIT); converts ImportSucceededEvent → JMS message
- `src/main/java/com/sunasterisk/socialanalytics/messaging/ImportEventListener.java` — @JmsListener(IMPORT_COMPLETED); recalculates per-platform stats; NAKs on exception → Artemis DLQ
- `src/main/java/com/sunasterisk/socialanalytics/messaging/ImportStatsCache.java` — AtomicReference<ImportStats>; in-memory stats cache updated by JMS listener

### Repositories
- `src/main/java/com/sunasterisk/socialanalytics/repository/PostRepository.java` — JpaRepository<Post>; findByStatus, countByPlatformAndStatus, findByStatusAndPlatformAndPlatformPostIdIn
- `src/main/java/com/sunasterisk/socialanalytics/repository/ImportBatchRepository.java` — JpaRepository<ImportBatch>
- `src/main/java/com/sunasterisk/socialanalytics/repository/UserRepository.java` — JpaRepository<User>; findByEmail
- `src/main/java/com/sunasterisk/socialanalytics/repository/SocialAccountRepository.java` — JpaRepository<SocialAccount>; findByProviderAndProviderAccountId
- `src/main/java/com/sunasterisk/socialanalytics/repository/SocialMetricRepository.java` — JpaRepository<SocialMetric>; findTopByPostIdOrderByCrawledAtDesc

### Entities
- `src/main/java/com/sunasterisk/socialanalytics/entity/BaseEntity.java` — Abstract; created_at / updated_at (@CreatedDate, @LastModifiedDate)
- `src/main/java/com/sunasterisk/socialanalytics/entity/User.java` — id, email, name, avatar_url, role(UserRole); extends BaseEntity
- `src/main/java/com/sunasterisk/socialanalytics/entity/Post.java` — id, user_id, platform(SocialProvider), platform_post_id, title, content, post_url, published_at, import_batch_id, status(PostStatus); extends BaseEntity
- `src/main/java/com/sunasterisk/socialanalytics/entity/SocialAccount.java` — id, user_id, provider, provider_account_id, access_token, refresh_token, token_expires_at
- `src/main/java/com/sunasterisk/socialanalytics/entity/SocialMetric.java` — id, post_id, likes_count, shares_count, comments_count, followers_count, reach, impressions, crawled_at
- `src/main/java/com/sunasterisk/socialanalytics/entity/ImportBatch.java` — id, user_id, file_name, total_records, success_records, failed_records, status(ImportBatchStatus), imported_at
- `src/main/java/com/sunasterisk/socialanalytics/entity/SocialProvider.java` — Enum: FACEBOOK, TWITTER
- `src/main/java/com/sunasterisk/socialanalytics/entity/PostStatus.java` — Enum: ACTIVE, DELETED
- `src/main/java/com/sunasterisk/socialanalytics/entity/ImportBatchStatus.java` — Enum: PENDING, PROCESSING, DONE, FAILED
- `src/main/java/com/sunasterisk/socialanalytics/entity/UserRole.java` — Enum: ADMIN, USER

### Security
- `src/main/java/com/sunasterisk/socialanalytics/security/CustomOAuth2UserService.java` — Extends DefaultOAuth2UserService; upserts User + SocialAccount; grants ROLE_USER authority

### DTOs
- `src/main/java/com/sunasterisk/socialanalytics/dto/PostResponse.java` — Post list item DTO
- `src/main/java/com/sunasterisk/socialanalytics/dto/MetricResponse.java` — Aggregated metrics response
- `src/main/java/com/sunasterisk/socialanalytics/dto/ExportRowModel.java` — Excel export row model
- `src/main/java/com/sunasterisk/socialanalytics/dto/ImportBatchResponse.java` — Import result summary DTO
- `src/main/java/com/sunasterisk/socialanalytics/dto/LastUpdatedResponse.java` — Last crawl timestamp DTO

### Utilities
- `src/main/java/com/sunasterisk/socialanalytics/util/ExcelColumn.java` — Annotation for Excel column mapping
- `src/main/java/com/sunasterisk/socialanalytics/util/ExcelRowMapper.java` — Reflection-based Excel row → DTO mapper
- `src/main/java/com/sunasterisk/socialanalytics/util/ReflectionRowWriter.java` — Reflection-based DTO → Excel row writer
- `src/main/java/com/sunasterisk/socialanalytics/util/ResourceNotFoundException.java` — Custom 404 exception

### Database Migrations
- `src/main/resources/db/migration/V1__init_schema.sql` — Creates all tables + enums + triggers + indexes (users, import_batches, posts, social_accounts, social_metrics)
- `src/main/resources/db/migration/V2__update_user_role_default.sql` — Alters user_role default

### Views (Thymeleaf)
- `src/main/resources/templates/login.html` — Login page (OAuth2 buttons, error/logout banners)
- `src/main/resources/templates/dashboard.html` — Dashboard view (user info, logout)

### Tests
- `src/test/java/com/sunasterisk/socialanalytics/` — Unit tests (ExcelImportServiceTest, PostServiceTest, MetricServiceTest, etc.) + JMS integration test (ImportJmsPipelineTest)

---

## Routes

| Method | Path | Controller | Description |
|--------|------|-----------|-------------|
| GET | / | DashboardController | Dashboard (Thymeleaf; auth required) |
| GET | /login | LoginController | Login page (Thymeleaf; public) |
| GET | /oauth2/authorization/facebook | Spring Security | Redirect to Facebook OAuth2 |
| GET | /oauth2/authorization/twitter | Spring Security | Redirect to Twitter OAuth2 |
| GET | /login/oauth2/code/facebook | Spring Security | Facebook OAuth2 callback |
| GET | /login/oauth2/code/twitter | Spring Security | Twitter OAuth2 callback |
| POST | /logout | Spring Security | Logout (CSRF-protected) |
| GET | /posts | PostController | List active posts (paginated) |
| DELETE | /posts/{id} | PostController | Soft-delete post (auth required) |
| POST | /import-posts | ImportController | Bulk import from .xlsx (multipart) |
| GET | /export-report | ExcelExportController | Export ACTIVE posts + metrics as .xlsx |
| GET | /metrics | MetricController | Aggregated metrics |
| GET | /metrics/last-updated | MetricController | Last crawl timestamp |
| GET | /swagger-ui.html | springdoc | Swagger UI (dev only) |
| GET | /v3/api-docs | springdoc | OpenAPI JSON spec (dev only) |

---

## Behavior Logic Inventory

| ID | Name | Source File |
|----|------|------------|
| BL001 | Validate-first import with all-or-nothing persistence | ExcelImportService.java |
| BL002 | Soft-delete (status=DELETED, not physical DELETE) | PostService.java |
| BL003 | Export: ACTIVE posts + latest metric snapshot per post | ExcelExportService.java |
| BL004 | OAuth2 upsert: find-or-create User + SocialAccount on login | CustomOAuth2UserService.java |
| BL005 | Scheduled crawl: @Async per-account metric fetch | CrawlJobService.java / SocialCrawlerService.java |
| BL006 | JMS publish only after DB commit (@TransactionalEventListener AFTER_COMMIT) | ImportEventProducer.java |
| BL007 | JMS listener: recalculate per-platform stats; NAK→DLQ on exception | ImportEventListener.java |

---

## Architecture

```
Browser / API Client
       │
       ▼
Spring Security Filter Chain
       │
       ├─ Public: /login, /oauth2/**, /swagger-ui/**, /v3/api-docs/**
       └─ Protected: all others → requires authenticated session
       │
       ▼
Controller Layer
  DashboardController  LoginController
  PostController       MetricController
  ImportController     ExcelExportController
       │
       ▼
Service Layer
  ExcelImportService  ExcelExportService  PostService
  MetricService       ImportBatchService
  CrawlJobService ──►  SocialCrawlerService (@Async)
       │
       ├──── publishes ImportSucceededEvent ─────────────────────►┐
       │                                                           │
       ▼                                                           ▼
Repository Layer                                     ImportEventProducer
  PostRepository        UserRepository               (@TransactionalEventListener
  ImportBatchRepository SocialAccountRepository       AFTER_COMMIT)
  SocialMetricRepository                                           │
       │                                                           │
       ▼                                                     JMS Queue
  PostgreSQL DB                                        IMPORT_COMPLETED (Artemis)
  (Flyway-managed schema)                                          │
                                                                   ▼
                                                     ImportEventListener
                                                     (@JmsListener)
                                                           │
                                                           ├── queries PostRepository
                                                           └── updates ImportStatsCache
                                                                (AtomicReference)
```

---

## Unresolved Questions

- None — full source tree surveyed.

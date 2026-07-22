# System Architecture

---
status: implemented
authored_by: takumi + rebuild-spec
updated: 2026-07-21
lang: en
---

## Overview

Social Analytics follows a layered Spring Boot architecture with three cross-cutting additions built incrementally: a Spring Security OAuth2 filter chain (D3), an async scheduled crawl pipeline (D4), and an embedded JMS import-stats pipeline (D5).

## Layered Architecture

```
HTTP Request
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│  Spring Security Filter Chain  (SecurityConfig)             │
│                                                             │
│  CsrfFilter → OAuth2LoginFilter → OAuth2CallbackFilter      │
│  → AuthorizationFilter (authenticated() on protected paths) │
│                                                             │
│  Public: /login, /oauth2/**, /login/oauth2/**,              │
│          /swagger-ui/**, /v3/api-docs/**, /error            │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
Controller Layer
  DashboardController (@Controller — Thymeleaf)
  LoginController     (@Controller — Thymeleaf)
  PostController      (@RestController — JSON)
  MetricController    (@RestController — JSON)
  ImportController    (@RestController — multipart)
  ExcelExportController (@RestController — binary)
  GlobalExceptionHandler (@RestControllerAdvice)
    │
    ▼
Service Layer
  ExcelImportService     — validate-first import; publishes ImportSucceededEvent
  ExcelExportService     — streams ACTIVE posts + metrics as .xlsx
  PostService            — paginated list; soft-delete
  MetricService          — aggregated metrics query
  ImportBatchService     — ImportBatch CRUD
  CrawlJobService        — @Scheduled orchestrator (delegates → @Async)
  SocialCrawlerService   — @Async per-account metric fetch
  CustomOAuth2UserService — OAuth2 upsert (User + SocialAccount)
    │
    ▼
Repository Layer (Spring Data JPA)
  PostRepository          SocialMetricRepository
  ImportBatchRepository   UserRepository
  SocialAccountRepository
    │
    ▼
Database
  PostgreSQL (Flyway-managed; ddl-auto=none)
  Tables: users, posts, import_batches, social_accounts, social_metrics
```

---

## D3: Security Layer & OAuth2 Client Integration

Spring Security inserts a filter chain in front of all controllers. OAuth2 authorization_code flow (with PKCE, auto-enabled in Spring Security 7) is configured for Facebook and Twitter/X. On successful OAuth2 callback, `CustomOAuth2UserService` upserts the `User` and `SocialAccount` records and grants `ROLE_USER`.

```
Browser → redirect to provider → callback → token exchange →
CustomOAuth2UserService (upsert User + SocialAccount) → session created
```

---

## D4: Async Crawl Pipeline

`CrawlJobService` is triggered by `@Scheduled(fixedDelayString = "${app.crawler.rate-ms:3600000}")`. It iterates all `SocialAccount` rows and delegates each to `SocialCrawlerService.crawl()` annotated `@Async`. The `@Async` proxy requires a separate bean boundary (CrawlJobService cannot call SocialCrawlerService's `@Async` method on itself). An `AsyncConfig` `@Bean` provides the backing `ThreadPoolTaskExecutor` (core=5, max=10, queue=100).

```
@Scheduled (1h fixedDelay)
    └─► CrawlJobService.runCrawl()
            └─► SocialCrawlerService.crawl(account)  [per account, @Async]
                    └─► fetch metrics from platform API
                    └─► persist SocialMetric row
```

---

## D5: JMS Import-Stats Pipeline

An embedded ActiveMQ Artemis broker (in-VM, no TCP) provides the `IMPORT_COMPLETED` queue. After `ExcelImportService` commits the import batch to the DB, it publishes an `ImportSucceededEvent` via Spring's `ApplicationEventPublisher`. `ImportEventProducer` consumes this event only AFTER the DB transaction commits (`@TransactionalEventListener(phase = AFTER_COMMIT)`) and converts it to a JMS `ImportCompletedMessage` (JSON/TextMessage via `MappingJackson2MessageConverter`). `ImportEventListener` (`@JmsListener`) receives the message, queries `PostRepository` for current ACTIVE counts, and updates `ImportStatsCache` (AtomicReference — lock-free).

```
ExcelImportService.persistSuccess()
    │
    ├─► DB commit (transaction boundary)
    │
    └─► ApplicationEventPublisher.publishEvent(ImportSucceededEvent)
            │
            ▼  (fires only after commit — AFTER_COMMIT guard)
        ImportEventProducer
            │
            ▼
        JMS Queue: IMPORT_COMPLETED  [Artemis in-VM]
            │
            ▼
        ImportEventListener (@JmsListener)
            │
            ├─► PostRepository.count()
            ├─► PostRepository.countByPlatformAndStatus(FACEBOOK, ACTIVE)
            ├─► PostRepository.countByPlatformAndStatus(TWITTER,  ACTIVE)
            └─► ImportStatsCache.update(stats)  [AtomicReference — lock-free]

On exception:
    ImportEventListener re-throws → Spring JMS NAKs →
    Artemis retries (max-delivery-attempts) →
    DLQ.IMPORT_COMPLETED (auto-created)
```

---

## Configuration Profiles

| Profile | DB | JMS broker | Swagger | DDL |
|---------|-----|-----------|---------|-----|
| dev | PostgreSQL (local .env) | Artemis embedded | Enabled | none (Flyway) |
| prod | PostgreSQL (env vars) | Artemis embedded | Disabled | none (Flyway) |
| test | H2 in-memory (UUID per context) | Artemis embedded | — | create-drop |

---

## Key Design Decisions

- **`@TransactionalEventListener(AFTER_COMMIT)`** — prevents phantom JMS messages when a DB transaction rolls back. The JMS publish is decoupled from the HTTP response path.
- **`MappingJackson2MessageConverter` (TextMessage)** — avoids Java serialization attack surface; type discriminator `_type` header preserves polymorphic deserialization safety.
- **`AtomicReference<ImportStats>`** — lock-free thread-safe stats cache; no contention on read path.
- **Two-tier event bridging** — `ImportSucceededEvent` (Spring event) → `ImportCompletedMessage` (JMS DTO) keeps `ExcelImportService` unaware of JMS infrastructure.
- **Embedded Artemis** — no external broker dependency for dev/test; topology identical across environments.

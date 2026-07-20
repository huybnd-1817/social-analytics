---
status: draft
authored_by: takumi
created: 2026-07-15
lang: en
---

# System Architecture — D4 Async/Scheduling Layer (forward-draft)

## D4 Additions: Background Execution Layer

D4 inserts an async/scheduling layer **alongside** the existing controller-service-repository stack. The new layer runs independently on a dedicated `ThreadPoolTaskExecutor` and is not triggered by HTTP requests.

```
┌─────────────────────────────────────────────────────────────────────┐
│  Spring Security Filter Chain (D3)                                   │
└─────────────────────────────────────────────────────────────────────┘
         │ (authenticated HTTP requests)
         ▼
Controller layer  (PostController, MetricController ← adds /last-updated,
                   DashboardController ← adds lastCrawledAt model attr, …)
         │
         ▼
Service layer     (PostService, MetricService, SocialCrawlerService ← NEW,
                   CrawlJobService ← NEW, …)
         │
         ▼
Repository layer  (PostRepository, SocialMetricRepository, …)
         │
         ▼
Database          (PostgreSQL)


── Background Thread Pool ───────────────────────────────────────────
  @Scheduled timer (CrawlJobService)
       │  fires every hour
       ▼
  CrawlJobService.updateSocialMetricsJob()
       │  PostRepository.findByStatus(ACTIVE) → [Post, …]
       │  for each Post → SocialCrawlerService.crawlPost(post)  [@Async]
       ▼
  ThreadPoolTaskExecutor  (core=5, max=10, queue=100, CallerRunsPolicy)
       │  parallel execution
       ▼
  SocialCrawlerService.crawlPost(post)
       │  generate mock metrics (ThreadLocalRandom)
       │  SocialMetricRepository.save(metric)
       ▼
  CompletableFuture<Void>  collected by CrawlJobService
       │  allOf(...).join()  → wait for all
       ▼
  CrawlJobService  updates AtomicReference<Instant> lastCrawledAt
                   logs duration + success/failure counts
```

## New Components

| Component | Type | Responsibility |
|-----------|------|----------------|
| `AsyncConfig` | `@Configuration` with `@EnableAsync @EnableScheduling` | Defines `ThreadPoolTaskExecutor` bean; registers `AsyncUncaughtExceptionHandler` |
| `SocialCrawlerService` | `@Service` | `@Async crawlPost(Post)` — mock metric generation, save `SocialMetric`, return `CompletableFuture<Void>` |
| `CrawlJobService` | `@Service` | `@Scheduled updateSocialMetricsJob()` — orchestrates fan-out, tracks `lastCrawledAt`, logs results |
| `LastUpdatedResponse` | DTO record | `{ lastCrawledAt: Instant? }` — serialized as ISO-8601 string or null |

## Property Additions (`application.properties`)

```properties
# D4: Thread pool
app.async.core-pool-size=5
app.async.max-pool-size=10
app.async.queue-capacity=100
app.async.thread-name-prefix=crawl-

# D4: Scheduler
app.crawler.rate-ms=3600000
```

## AsyncUncaughtExceptionHandler

Registered on `AsyncConfigurer`; logs `[ASYNC-ERROR] method={name} args={args} ex={message}` at ERROR level. Does not re-throw — individual post failures are isolated.

## Integration with Existing Components

- `MetricController` gains `GET /metrics/last-updated` — injects `CrawlJobService`, returns `LastUpdatedResponse`.
- `DashboardController` injects `CrawlJobService` — adds `lastCrawledAt` (`Instant` or `null`) to Thymeleaf model.
- `PostRepository.findByStatus(PostStatus.ACTIVE)` already exists — no new query needed.
- `SocialMetricRepository.save()` already exists — no new method needed.

## No Schema Change

`SocialMetric.crawledAt: Instant` already captures the per-crawl timestamp. `lastCrawledAt` (job-level) is held in-memory only.

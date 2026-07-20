---
status: draft
authored_by: takumi
created: 2026-07-15
lang: en
---

# Automated Crawl Job (D4) — Technical Spec

## Overview

D4 adds a background thread-pool executor and a scheduled hourly job that crawls mock social metrics for all active posts. Each post crawl runs asynchronously on a `ThreadPoolTaskExecutor`. A `GET /metrics/last-updated` endpoint and a "Last Updated" section on the dashboard UI surface the last-crawl timestamp to authenticated users.

**New execution layer introduced:** Spring `@Async` + `@Scheduled` on top of the existing layered architecture. No schema changes (existing `SocialMetric.crawledAt` suffices). Last-crawl timestamp is tracked in-memory via `AtomicReference<Instant>`.

## Polymorphic Behavior

None. Single executor config, single scheduled job. No role-based branching.

## Cross-Cutting Logic

### Requirements

- R1: `@EnableAsync` and `@EnableScheduling` must be enabled via `AsyncConfig @Configuration` (not on the main application class).
- R2: `ThreadPoolTaskExecutor` core/max pool size, queue capacity, and thread-name prefix must be externalized to `application.properties` under `app.async.*`.
- R3: `SocialCrawlerService.crawlPost(Post)` is annotated `@Async`, runs on the thread pool, generates mock metrics, saves `SocialMetric`, returns `CompletableFuture<Void>`.
- R4: Mock metric generation uses `ThreadLocalRandom` to produce plausible but random values; platform (FACEBOOK vs. TWITTER) seeds different ranges.
- R5: `CrawlJobService.updateSocialMetricsJob()` is annotated `@Scheduled(fixedRateString = "${app.crawler.rate-ms:3600000}")`, fetches `PostStatus.ACTIVE` posts, fans out via `crawlPost()`, waits for all futures, logs duration + success/failure counts.
- R6: On job completion (regardless of individual failures), `CrawlJobService` updates `AtomicReference<Instant> lastCrawledAt`.
- R7: `AsyncUncaughtExceptionHandler` is registered in `AsyncConfig`; logs method name + params + exception without re-throwing.
- R8: `GET /metrics/last-updated` returns `{ "lastCrawledAt": "<ISO-8601 or null>" }`.
- R9: `DashboardController` injects `CrawlJobService`; adds `lastCrawledAt` attribute to the model.
- R10: `dashboard.html` shows "Last crawled: {timestamp}" or "Never" when `lastCrawledAt` is null.
- R11: Tests for `SocialCrawlerService`: mock `SocialMetricRepository`, call `crawlPost()`, assert `save()` called once with correct `Post` ref and positive metric values.

### Security Notes

- `GET /metrics/last-updated` is a `@RestController` endpoint under `SecurityConfig`'s `anyRequest().authenticated()` — no special permit needed beyond what D3 already enforces.
- No sensitive data exposed — timestamp only.

## User Stories

### US-D4-01 — Hourly metrics auto-refresh

**Actor:** System (background job)
**Goal:** Keep `SocialMetric` rows up-to-date without manual trigger.

**Happy Path:**
1. `@Scheduled` fires every hour (configurable via `app.crawler.rate-ms`).
2. `CrawlJobService` fetches all `ACTIVE` posts.
3. `crawlPost()` is dispatched for each post on the thread pool (async, parallel).
4. `SocialMetric` row saved per post with `crawledAt = Instant.now()`.
5. `lastCrawledAt` updated; job duration + counts logged at INFO level.

**Failure Path:**
- Individual post crawl throws: exception caught by `AsyncUncaughtExceptionHandler`, logged; other posts continue.
- All crawls fail: `lastCrawledAt` still updated (job ran); failure count logged.

### US-D4-02 — Authenticated user views last crawl time

**Actor:** Authenticated user (any provider)
**Goal:** Know when metrics were last refreshed.

**Happy Path:**
1. User opens dashboard at `GET /`.
2. `DashboardController` reads `CrawlJobService.getLastCrawledAt()`.
3. Template renders "Last crawled: 2026-07-15T14:00:00Z" (or "Never" if job hasn't run yet).

### US-D4-03 — API consumer polls last crawl timestamp

**Actor:** API consumer
**Goal:** Check when metrics were last updated programmatically.

**Happy Path:**
1. `GET /metrics/last-updated` returns `{ "lastCrawledAt": "2026-07-15T14:00:00Z" }`.
2. If job has never run: `{ "lastCrawledAt": null }`.

## Key Entities

| Entity | Change | Notes |
|--------|--------|-------|
| `SocialMetric` | None | `crawledAt` already exists; D4 sets it to `Instant.now()` at crawl time |
| `Post` | None | D4 reads via `PostRepository.findByStatus(PostStatus.ACTIVE)` |

No schema migration needed.

## Artifact References

| Artifact | Path |
|----------|------|
| Async config | `src/main/java/.../config/AsyncConfig.java` (new) |
| Crawler service | `src/main/java/.../service/SocialCrawlerService.java` (new) |
| Crawl job service | `src/main/java/.../service/CrawlJobService.java` (new) |
| Last-updated DTO | `src/main/java/.../dto/LastUpdatedResponse.java` (new) |
| MetricController | `src/main/java/.../controller/MetricController.java` (add `/last-updated` endpoint) |
| DashboardController | `src/main/java/.../controller/DashboardController.java` (add `lastCrawledAt` to model) |
| dashboard.html | `src/main/resources/templates/dashboard.html` (add Last Updated section) |
| application.properties | `src/main/resources/application.properties` (add `app.async.*`, `app.crawler.*`) |
| Crawler test | `src/test/java/.../service/SocialCrawlerServiceTest.java` (new) |

## Assumptions

- A1: Mock metrics (no real FB/TW API calls) — production API integration is out of scope for D4.
- A2: In-memory `AtomicReference<Instant>` for `lastCrawledAt` — survives restart only within same JVM session; DB persistence deferred.
- A3: Scheduled rate is configurable via `app.crawler.rate-ms`; default 3600000 ms (1 hour).
- A4: Tests run with `@ExtendWith(MockitoExtension.class)` — `@Async` does not fire in plain Mockito tests; `crawlPost()` runs synchronously, `CompletableFuture.get()` is called to unwrap.
- A5: `PostRepository.findByStatus(PostStatus.ACTIVE)` returns an unbounded list; demo-scale acceptable.

## Source Code References

- `PostRepository.findByStatus(PostStatus)` — `src/main/java/.../repository/PostRepository.java:22`
- `SocialMetric.crawledAt` — `src/main/java/.../entity/SocialMetric.java:51`
- `MetricController` — `src/main/java/.../controller/MetricController.java`
- `DashboardController` — `src/main/java/.../controller/DashboardController.java`

## Unresolved Questions

None.

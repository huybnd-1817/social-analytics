---
title: "D4: Background Jobs & Multithreading"
description: "Thread-pool executor + hourly scheduled mock-metric crawl job, with last-updated API and dashboard row."
status: completed
priority: P2
effort: 5h
branch: feature/automated-crawl-job
tags: [async, scheduling, background-jobs, crawler]
created: 2026-07-15
spec: docs/features/F006_AutomatedCrawlJob/
spec_lang: en
---

# D4: Background Jobs & Multithreading

Spring `@Async` + `@Scheduled` execution layer over the existing layered architecture.
An hourly job fetches ACTIVE posts and fans each out to an async crawler that generates
mock FB/TW metrics and saves a `SocialMetric`. Last-crawl timestamp (in-memory
`AtomicReference<Instant>`) is exposed via `GET /metrics/last-updated` and the dashboard.

**No schema changes** — existing `SocialMetric.crawledAt` suffices.

## Phases

| # | Phase | Tasks | Status | Depends on |
|---|-------|-------|--------|------------|
| 01 | [Async config + properties](phase-01-async-config.md) | D4-01, D4-02, D4-08 | completed | — |
| 02 | [Crawler service (async)](phase-02-crawler-service.md) | D4-03 | completed | 01 |
| 03 | [Crawl job service + lastCrawledAt](phase-03-crawl-job-service.md) | D4-04, D4-05 | completed | 02 |
| 04 | [Last-updated API + dashboard UI](phase-04-api-and-ui.md) | D4-06, D4-07 | completed | 03 |
| 05 | [Crawler unit tests](phase-05-tests.md) | D4-09 | completed | 02 |

## Dependency Graph

```
01 (config) ──► 02 (crawler) ──► 03 (job) ──► 04 (api+ui)
                     └──────────► 05 (tests)
```

Phase 05 depends only on 02 (tests the crawler bean); it can run in parallel with 03/04.

## Key Constraints

- `AsyncConfig implements AsyncConfigurer` — registers `AsyncUncaughtExceptionHandler`.
  `@EnableAsync` + `@EnableScheduling` live here, NOT on the main app class.
- `crawlPost()` must sit in a **separate bean** from its caller (`CrawlJobService`) —
  Spring's `@Async` proxy is bypassed on self-invocation.
- `CompletableFuture<Void>` fan-out: crawler returns it; job does `allOf(...).join()`.
- Tests use plain Mockito (`@ExtendWith(MockitoExtension.class)`) — `@Async` does NOT
  fire, `crawlPost()` runs synchronously; call `.get()` to unwrap the future.

## File Ownership (no cross-phase collisions)

- P01: `config/AsyncConfig.java` (new), `application.properties` (async/crawler block)
- P02: `service/SocialCrawlerService.java` (new)
- P03: `service/CrawlJobService.java` (new)
- P04: `dto/LastUpdatedResponse.java` (new), `MetricController.java`, `DashboardController.java`, `dashboard.html`
- P05: `service/SocialCrawlerServiceTest.java` (new)

## Success Criteria

- App boots; scheduled job fires (verify via log at reduced `app.crawler.rate-ms`).
- `SocialMetric` rows saved per ACTIVE post with positive values + `crawledAt` set.
- `GET /metrics/last-updated` returns timestamp (or null before first run).
- Dashboard shows "Last crawled: …" / "Never".
- `SocialCrawlerServiceTest` passes; full build green (`mvn -q test`).

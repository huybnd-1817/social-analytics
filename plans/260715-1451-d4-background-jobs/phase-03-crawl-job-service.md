# Phase 03 — Crawl Job Service + lastCrawledAt (D4-04, D4-05)

## Context Links
- Spec: `spec/automated-crawl-job/technical-spec.md` (R5, R6; US-D4-01)
- Repo: `repository/PostRepository.java` (`List<Post> findByStatus(PostStatus)` at line 22)
- Depends on: Phase 02 (`SocialCrawlerService`), Phase 01 (`@EnableScheduling`)

## Overview
- **Priority:** P1
- **Status:** completed
- **Description:** Scheduled hourly job that fetches ACTIVE posts, fans each out to
  `crawlPost()`, waits on all futures, logs duration + success/failure counts, and
  updates an in-memory `AtomicReference<Instant> lastCrawledAt`.

## Key Insights
- Uses the **unpaged** `PostRepository.findByStatus(PostStatus.ACTIVE)` (already exists).
- `@Scheduled(fixedRateString = "${app.crawler.rate-ms:3600000}")` — default 1h; the
  property was declared in Phase 01.
- Collect `CompletableFuture<Void>` per post → `CompletableFuture.allOf(arr).join()`.
- `lastCrawledAt` updated in a `finally` block so it reflects "job ran" even if some
  crawls failed (spec R6). Exposed via `getLastCrawledAt(): Instant` (may be null).
- Success/failure counting: since void-async exceptions go to the uncaught handler (not
  the future), count success by iterating futures with `isCompletedExceptionally()` after
  `allOf`. (Crawler returns completed futures, so failures manifest as exceptions during
  dispatch — wrap per-post dispatch to tally.)

## Requirements
- Functional: R5 (scheduled fan-out + wait + log), R6 (always update `lastCrawledAt`).
- Non-functional: bounded by pool (Phase 01); `join()` blocks the scheduler thread until
  the batch drains — acceptable at demo scale (A5).

## Architecture
```
@Scheduled updateSocialMetricsJob()
  ├─ start = Instant.now()
  ├─ posts = postRepository.findByStatus(ACTIVE)
  ├─ futures = posts.map(p -> safeCrawl(p))          // safeCrawl catches dispatch errors
  ├─ CompletableFuture.allOf(futures).join()
  ├─ tally success/failure from futures
  ├─ finally: lastCrawledAt.set(Instant.now())
  └─ log.info("crawl done: {} ok, {} failed, {} ms", ...)

getLastCrawledAt(): Instant   // lastCrawledAt.get(), consumed by Phase 04
```
Input: DB ACTIVE posts. Output: N `SocialMetric` rows (via crawler) + updated timestamp + log line.

## Related Code Files
- **Create:** `src/main/java/com/sunasterisk/socialanalytics/service/CrawlJobService.java`
- **Read:** `service/SocialCrawlerService.java`, `repository/PostRepository.java`

## Implementation Steps
1. `@Service @RequiredArgsConstructor @Slf4j`; inject `SocialCrawlerService`, `PostRepository`.
2. Field: `private final AtomicReference<Instant> lastCrawledAt = new AtomicReference<>();`.
3. `@Scheduled(fixedRateString = "${app.crawler.rate-ms:3600000}") public void updateSocialMetricsJob()`:
   - record `start`; fetch ACTIVE posts.
   - build `List<CompletableFuture<Void>>` via `crawlPost(p)` (wrap in try/catch to count
     synchronous dispatch failures; add `CompletableFuture.failedFuture(ex)` on catch).
   - `CompletableFuture.allOf(list.toArray(new CompletableFuture[0])).join()`.
   - count `failed = futures.stream().filter(CompletableFuture::isCompletedExceptionally).count()`.
   - `finally { lastCrawledAt.set(Instant.now()); }`.
   - `log.info` duration (`Duration.between(start, Instant.now()).toMillis()`) + ok/failed counts.
4. `public Instant getLastCrawledAt() { return lastCrawledAt.get(); }`.

## Todo List
- [ ] Create `CrawlJobService` with `@Scheduled` job
- [ ] `AtomicReference<Instant> lastCrawledAt`, updated in `finally`
- [ ] Fan-out via injected `SocialCrawlerService.crawlPost`, `allOf(...).join()`
- [ ] Log duration + success/failure counts
- [ ] `getLastCrawledAt()` accessor
- [ ] `mvn -q compile` clean

## Success Criteria
- With `app.crawler.rate-ms` lowered (e.g. 10000), log shows periodic "crawl done" lines.
- `SocialMetric` count grows per ACTIVE post per run.
- `getLastCrawledAt()` non-null after first run.

## Risk Assessment
| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| `join()` on empty future array | Med | Low | `allOf()` of empty array completes immediately — safe |
| No ACTIVE posts → empty batch | Med | Low | Job still updates `lastCrawledAt`; logs "0 ok" |
| Scheduler thread starved by long `join()` | Low | Med | Demo scale; single scheduled method — no contention |
| Overlapping runs if job > rate | Low | Med | Default single-threaded scheduler serializes runs |

## Security Considerations
- None new. Timestamp only; no user data in logs.

## Next Steps
- Unblocks Phase 04 (`getLastCrawledAt()` feeds API + dashboard).

## Rollback
- Delete `CrawlJobService.java`. In-memory state vanishes on restart (A2).

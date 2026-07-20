# Phase 02 — Crawler Service (D4-03)

## Context Links
- Spec: `spec/automated-crawl-job/technical-spec.md` (R3, R4)
- Entities: `entity/{Post,SocialMetric,SocialProvider}.java`
- Repo: `repository/SocialMetricRepository.java`

## Overview
- **Priority:** P1
- **Status:** completed
- **Description:** `SocialCrawlerService.crawlPost(Post)` — `@Async`, generates mock
  platform-seeded metrics via `ThreadLocalRandom`, saves a `SocialMetric`, returns
  `CompletableFuture<Void>`.

## Key Insights
- MUST be a **separate `@Service` bean** from `CrawlJobService` (Phase 03). Spring's
  `@Async` proxy is bypassed when a method calls a sibling `@Async` method on `this`.
- `ThreadLocalRandom.current()` — no shared `Random`, safe under the thread pool.
- `SocialMetric` uses Lombok `@Builder`; `crawledAt = Instant.now()` set explicitly
  (`createdAt` is `@CreatedDate`, auto-filled by auditing).
- Return `CompletableFuture.completedFuture(null)` so the caller can `allOf(...).join()`.

## Requirements
- Functional: R3 (async crawl+save+future), R4 (platform-seeded mock ranges).
- Ranges: FACEBOOK likes 100–1000, shares 10–200; TWITTER likes 50–500, shares 5–100;
  common comments 1–50, followers 200–5000, reach 500–10000, impressions 1000–50000.

## Architecture
```
crawlPost(Post p)  [@Async, runs on crawler-* pool thread]
  ├─ ThreadLocalRandom → likes/shares by p.getPlatform() (FACEBOOK|TWITTER)
  ├─ common metrics (comments, followers, reach, impressions)
  ├─ SocialMetric.builder()...post(p).crawledAt(Instant.now()).build()
  ├─ socialMetricRepository.save(metric)
  └─ return CompletableFuture.completedFuture(null)
```
Input: one `Post` (ACTIVE). Output: persisted `SocialMetric` + completed future.

## Related Code Files
- **Create:** `src/main/java/com/sunasterisk/socialanalytics/service/SocialCrawlerService.java`
- **Read:** `entity/SocialMetric.java`, `entity/SocialProvider.java`, `repository/SocialMetricRepository.java`

## Implementation Steps
1. `@Service @RequiredArgsConstructor @Slf4j` class; inject `SocialMetricRepository`.
2. Private helper `long randomInRange(long min, long max)` using
   `ThreadLocalRandom.current().nextLong(min, max + 1)`.
3. `@Async public CompletableFuture<Void> crawlPost(Post post)`:
   - switch/if on `post.getPlatform()`:
     - FACEBOOK → likes 100–1000, shares 10–200
     - TWITTER → likes 50–500, shares 5–100
   - common: comments 1–50, followers 200–5000, reach 500–10000, impressions 1000–50000.
   - build `SocialMetric` (post, all counts, `crawledAt(Instant.now())`), `save()`.
   - `log.debug` per-post crawl (post id + platform).
   - `return CompletableFuture.completedFuture(null)`.

## Todo List
- [ ] Create `SocialCrawlerService` (@Service, separate bean)
- [ ] `@Async` on `crawlPost`, returns `CompletableFuture<Void>`
- [ ] Platform-seeded ranges + common metrics via `ThreadLocalRandom`
- [ ] Save `SocialMetric` with `crawledAt = Instant.now()`
- [ ] `mvn -q compile` clean

## Success Criteria
- Compiles; bean distinct from `CrawlJobService`.
- Verified by Phase 05 unit test (save called once, positive values).

## Risk Assessment
| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| `@Async` self-invocation if merged with job service | Med | High | Enforce separate bean (this phase owns only the crawler) |
| Off-by-one in `nextLong` upper bound (exclusive) | Med | Low | Use `max + 1`; assert `> 0` in tests |
| New `SocialProvider` value unhandled | Low | Low | Only FB/TW exist; default branch → common-only or FB ranges |

## Security Considerations
- Mock data only; no external API calls, no credentials.

## Next Steps
- Unblocks Phase 03 (job fans out to this) and Phase 05 (unit tests).

## Rollback
- Delete `SocialCrawlerService.java`. No persisted state beyond test-run rows.

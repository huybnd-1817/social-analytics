---
name: d6-integration-tests
description: Full integration tests for Excel import pipeline (D6-09) and crawl job + WebSocket broadcast (D6-10), plus suite coverage gate (D6-11)
metadata:
  type: feature
  tasks: D6-09 to D6-11
---

## Feature: Integration Tests — Import Pipeline & Crawl Job

### Scope
Two `@SpringBootTest` integration tests covering the end-to-end flows that unit tests cannot verify, plus a coverage gate.

### Test 1 — ExcelImportIntegrationTest (D6-09)
Flow: `POST /import-posts` (MockMvc + `@WithMockUser`) → `ExcelImportService` → `PostRepository.saveAll` → `ImportSucceededEvent` → `ImportEventProducer` (AFTER_COMMIT) → JMS queue → `ImportEventListener` → `ImportStatsCache`

Assertions:
- HTTP 200, body `status=DONE`, `successRecords=2`
- `postRepository.count()` increased by 2
- `importStatsCache.get()` present within 5 s (Awaitility)
- `importStatsCache.get().totalPosts()` ≥ 2

### Test 2 — CrawlJobIntegrationTest (D6-10)
Flow: direct call to `crawlJobService.updateSocialMetricsJob()` → `@Async crawlPost()` per post → `SocialMetricRepository.save()` → `metricsBroadcaster.broadcast("CRAWL_COMPLETE")`

Assertions:
- `socialMetricRepository.count()` increased by (number of seeded posts)
- `metricsBroadcaster.broadcast("CRAWL_COMPLETE")` called exactly once (via `@MockitoSpyBean`)

### Constraints
- NO `@Transactional` on tests — `@TransactionalEventListener(AFTER_COMMIT)` requires actual DB commit
- Manual cleanup in `@AfterEach` (deleteAll in reverse FK order)
- Seed User required for `ImportBatchService.resolveSeedUser()`
- Scheduling disabled in `test` profile — call job directly
- `@SpringBootTest` loads full context including Artemis embedded broker

### New Files
| File | Purpose |
|------|---------|
| `integration/ExcelImportIntegrationTest.java` | D6-09 import pipeline integration test |
| `integration/CrawlJobIntegrationTest.java` | D6-10 crawl + WebSocket broadcast integration test |

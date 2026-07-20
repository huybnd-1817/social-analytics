---
status: draft
authored_by: takumi
created: 2026-07-15
lang: en
---

# Edge Cases — D4 Automated Crawl Job

| ID | Scenario | Expected Behaviour |
|----|----------|--------------------|
| EC-01 | No `ACTIVE` posts in DB when job fires | Job logs "0 posts to crawl", skips fan-out, still updates `lastCrawledAt` |
| EC-02 | Single post crawl throws runtime exception | `AsyncUncaughtExceptionHandler` logs the error; remaining posts continue; failure count incremented |
| EC-03 | All post crawls fail | `lastCrawledAt` updated (job ran); log: success=0, failed=N |
| EC-04 | `GET /metrics/last-updated` before job has ever run | Returns `{ "lastCrawledAt": null }` — 200 OK, not 404 |
| EC-05 | Job fires while previous job still in progress | `@Scheduled(fixedRate=...)` fires on a fixed rate — overlap is possible if prior run exceeds 1 hour. Both runs share the same thread pool; executor queue absorbs the backlog. For demo scale this is acceptable. |
| EC-06 | Dashboard loaded before job runs | `lastCrawledAt` model attribute is `null`; template displays "Never" |
| EC-07 | Thread pool queue at capacity | Tasks rejected by executor → `CallerRunsPolicy` (configured in `AsyncConfig`); caller thread executes the crawl synchronously |

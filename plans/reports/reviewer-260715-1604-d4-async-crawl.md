## Code Review Summary — D4 Async Crawl Job

### Scope
- Files: 8 source + 4 test files
- LOC: ~350 source / ~200 test
- Focus: async proxy correctness, thread safety, security, test quality

### Overall Assessment
Solid, well-reasoned implementation. The key design decisions (separate bean for `@Async`, `CallerRunsPolicy`, `AtomicReference`, `try/catch + finally`) are all correct and production-aware. One medium bug and two warnings found; no security issues with the new endpoints.

---

### Critical Issues

None.

---

### High Priority

**H1 — `spring.task.scheduling.enabled=false` has no effect (test reliability)**

`application-test.properties` sets `spring.task.scheduling.enabled=false`, but this is not a recognized Spring Boot property. It silently does nothing. The scheduler remains fully active in any context that loads `AsyncConfig`.

The second line `app.crawler.rate-ms=9999999999` prevents repeat fires, but `@Scheduled(fixedRateString=...)` **fires immediately on first startup** before the rate interval begins. Any `@SpringBootTest` that loads the full context will trigger one execution of `updateSocialMetricsJob()` at startup.

`SocialAnalyticsApplicationTests` has no `@ActiveProfiles("test")`, so it runs with the `dev` profile, loads `AsyncConfig`, and the job fires against the `localhost:5432` dev database. Currently masked because the test passes when Postgres is up, but it adds noise metrics to the dev DB and the test becomes environment-sensitive.

Fix options (pick one):
- Add `@ActiveProfiles("test")` to `SocialAnalyticsApplicationTests` (simplest).
- Replace fake property with `@ConditionalOnProperty(name = "app.crawler.enabled", havingValue = "true", matchIfMissing = true)` on `CrawlJobService`, then set `app.crawler.enabled=false` in `application-test.properties`.
- Add `initialDelay` to the `@Scheduled` annotation (`initialDelayString = "${app.crawler.initial-delay-ms:0}"`) and set a large value in test properties.

**H2 — `SocialCrawlerService.crawlPost()` has no `@Transactional`; detached `Post` passed across thread boundary**

`CrawlJobService.updateSocialMetricsJob()` loads `Post` entities via `postRepository.findByStatus()`. With `spring.jpa.open-in-view=false` (correctly set), those entities are detached once the repository method returns. They are then handed to `@Async` worker threads where `SocialCrawlerService` calls `em.persist(metric)` with `metric.post` pointing to a detached entity.

Hibernate 6 handles this gracefully (it only needs the entity ID for the FK column), and in practice this works today. However:
- JPA spec does not guarantee `em.persist()` succeeds with a detached relationship.
- `@Transactional` on `crawlPost()` would open a fresh session, make the association explicit, and remove the ambiguity. It also avoids a potential `LazyInitializationException` if anything later attempts to navigate a lazy property of `Post` inside `crawlPost()`.

Recommended fix: add `@Transactional` to `crawlPost()`.

---

### Medium Priority

**M1 — `@Scheduled(fixedRate)` has no overlap guard**

With `fixedRateString`, a new execution fires every `rate-ms` regardless of whether the previous one has finished. If crawling N posts takes longer than the interval (e.g., after DB slowdown), two executions run concurrently and both write metrics for the same posts. For an analytics app this produces double-counted metric snapshots.

Fix: switch to `fixedDelayString` (fires `rate-ms` after the previous run completes) or add `@SchedulerLock` (ShedLock) if clustered deployment is planned.

**M2 — `Instant` rendered raw in Thymeleaf dashboard**

`th:text="${lastCrawledAt}"` calls `Instant.toString()` → `"2026-07-15T07:00:01Z"`. Functional but not user-friendly. A `#temporals` / `#dates` utility or a `DateTimeFormatter` in the controller would yield a more readable value. Low urgency for a demo dashboard.

**M3 — N independent INSERT transactions per crawl run**

Each `socialCrawlerService.crawlPost(post)` issues one `INSERT` in its own transaction. For 100 active posts this is 100 DB round-trips. Acceptable for demo scale; note as a known limitation if the table grows.

---

### Low Priority

**L1 — DashboardControllerTest does not assert `lastCrawledAt` model attribute**

The controller sets `model.addAttribute("lastCrawledAt", ...)` but no test verifies it. The D4 addition (and its null case) is untested at the slice level. The template behavior is exercised by `SocialCrawlerServiceTest` indirectly, but a direct `model().attribute("lastCrawledAt", ...)` assertion in `DashboardControllerTest` would close the gap.

**L2 — `CrawlJobServiceTest.job_crawlThrows_stillSetsLastCrawledAt` tests dispatch failure path only**

`safeCrawl` catches dispatch-time exceptions and converts them to `CompletableFuture.failedFuture()`. The test covers this path. However, it does not test the case where `crawlPost` returns a future that completes exceptionally (i.e., the async task itself fails after dispatch). That path is logically equivalent here but worth a note for completeness.

---

### Positive Observations

- Separating `SocialCrawlerService` from `CrawlJobService` to avoid the `@Async` self-invocation proxy trap is exactly right and well-documented in the class Javadoc.
- `@EnableAsync` / `@EnableScheduling` kept off the main application class so `@WebMvcTest` slices don't activate async wiring — good isolation discipline.
- `CallerRunsPolicy` prevents silent task drops when the queue is full. Correct choice over `AbortPolicy` for a background data pipeline.
- `AtomicReference<Instant>` is the right tool: single-writer (scheduler thread), multiple-reader, no lock needed.
- `try/catch + finally` pattern ensures `lastCrawledAt` is always stamped even on partial failure — correct.
- `safeCrawl()` wrapping dispatch errors in `CompletableFuture.failedFuture()` keeps failure counting accurate.
- `GET /metrics/last-updated` returns `{"lastCrawledAt": null}` before first run — correct null-safe contract.
- Auth: both new endpoints fall under `anyRequest().authenticated()` — no exposure risk.
- Test assertions on `isBetween()` bounds match the `ThreadLocalRandom.nextLong(origin, bound)` exclusive-upper-bound semantics exactly.

---

### Recommended Actions

1. **[High]** Add `@ActiveProfiles("test")` to `SocialAnalyticsApplicationTests` to prevent the crawler firing against the dev DB during `contextLoads`.
2. **[High]** Remove the noop `spring.task.scheduling.enabled=false` comment/property; replace with a real guard (conditional bean or `initialDelay` property).
3. **[High]** Add `@Transactional` to `SocialCrawlerService.crawlPost()` to remove detached-entity ambiguity.
4. **[Medium]** Switch `@Scheduled` from `fixedRateString` to `fixedDelayString` to prevent overlapping executions.
5. **[Low]** Add `lastCrawledAt` model attribute assertion in `DashboardControllerTest`.

---

**Status:** DONE_WITH_CONCERNS
**Score:** 8/10
**Critical issues:** none
**Concerns:** H1 (fake scheduler-disable property + missing @ActiveProfiles on SpringBootTest), H2 (detached entity passed to async persist without @Transactional — works today with Hibernate 6 but is spec-undefined), M1 (fixedRate overlap risk).

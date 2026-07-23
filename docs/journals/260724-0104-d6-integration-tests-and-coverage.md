# D6-09 to D6-11: Final Integration Test Block & Coverage Gate

**Date**: 2026-07-24 01:04  
**Severity**: Medium (completed feature, but with hard-won debugging lessons)  
**Component**: Test Suite, Excel Import Pipeline, Crawl Job Pipeline, Coverage Enforcement  
**Status**: Resolved

## What Happened

Implemented the final three integration test tasks (D6-09, D6-10, D6-11) from a 6-day Spring Boot learning sprint. This caps a full pipeline: user uploads Excel → posts persist → JMS fires → stats update → realtime broadcast. All 100 tests pass, 70% coverage gate enforced.

### D6-09 — ExcelImportIntegrationTest

Full `@SpringBootTest` integration test covering the Excel import → JMS → stats update pipeline:

- **POST /import-posts** → `ExcelImportService` (commits TX) → fires `ImportSucceededEvent` → `ImportEventProducer` publishes `IMPORT_COMPLETED` → `ImportEventListener` consumes → `ImportStatsCache` updated → `MetricsBroadcaster` broadcasts
- **3 test cases**:
  - TC-01: Valid upload (posts created, stats cached, broadcast verified via Awaitility)
  - TC-02: Duplicate upload (marked FAILED, no new rows, stats unchanged)
  - TC-03: Unauthenticated access (302 redirect to `/login`)
- **Critical insight**: NO `@Transactional` wrapper on the test — `@TransactionalEventListener(AFTER_COMMIT)` only fires on real commit; a rolled-back test TX swallows the event entirely

### D6-10 — CrawlJobIntegrationTest

`@SpringBootTest` integration test for the crawl job pipeline:

- **`CrawlJobService.updateSocialMetricsJob()`** (scheduled) → calls `@Async SocialCrawlerService.crawlPost()` per ACTIVE post → saves `SocialMetric` → calls `MetricsBroadcaster.broadcast("CRAWL_COMPLETE")`
- **3 test cases**:
  - TC-01: One metric per ACTIVE post, broadcast fires exactly once
  - TC-02: DELETED posts skipped (soft delete respected)
  - TC-03: `lastCrawledAt` strictly advances (captures before-value, asserts `isAfter(before)`)
- **Key discovery**: `@Async` (outer proxy) + `@Transactional` (inner proxy) ordering is why `CompletableFuture.allOf().join()` is sufficient for synchronization — the TX commits inside the async task before the future resolves

### D6-11 — Coverage Gate

- Added Jacoco Maven plugin to `pom.xml` with 70% INSTRUCTION_COVERAGE_RATIO gate bound to verify phase
- **Result**: 100 tests, 0 failures, BUILD SUCCESS, coverage ≥ 70%

## The Brutal Truth

Six hours of chasing test ordering races, proxy ordering, timing windows, and Spring security plumbing. The code was right; the test wiring was fragile. Each bug felt trivial until you traced it to the Spring context layering, and then it bit hard.

The exasperation came from knowing the feature worked (manual testing proved it), but tests kept failing because the wiring didn't match the docs I was reading. Spring Boot 4.x changed annotation paths in silence; old patterns from tutorials silently broke.

## Technical Details

### Bugs Caught and Fixed During Review

1. **Wrong `@AutoConfigureMockMvc` package** — Spring Boot 4.x moved it to `org.springframework.boot.webmvc.test.autoconfigure`, not the old `org.springframework.test.web.servlet.setup` path. Import error buried in pom.xml autocomplete.

2. **`@WithMockUser` doesn't work in `@SpringBootTest`** — it assumes the test has direct control over the security filter chain. In `@SpringBootTest`, the full Spring context is booted; you must use `SecurityMockMvcRequestPostProcessors.user()` per-request instead. The annotation silently does nothing; tests passed when they shouldn't.

3. **TC-02 timing race** — first import succeeds, stats cached; second import with same data fails as expected. But assertion on stats happens immediately after, before the first `ImportEventListener` finishes consuming and updating the cache. Added `Awaitility.await()` between imports to let JMS consumer settle. Without it: test passed locally, failed on CI.

4. **`findAll().get(0)` ordering not guaranteed** — queried posts but didn't sort; on different DB runs, the first post might be any post. Replaced with `findFirstByOrderByIdAsc().orElseThrow()` for determinism.

5. **`CrawlJobService` singleton persists `lastCrawledAt` across tests** — Spring context is shared across test methods in the same class. The `AtomicReference<Instant>` field in `CrawlJobService` was not reset between tests. Test-1 sets it to T1, Test-2 expects null or a fresh value but sees T1. Removed fragile `isNull()` pre-assertion, instead captured the before-value and asserted `isAfter(before)`.

### Error Strings & Artifacts

**Before fix (TC-02 race):**
```
FAILED: expected <0> but was <2> in importStatsCache.facebook_count
```
The stats hadn't been updated yet when the assertion ran.

**Before fix (lastCrawledAt singleton):**
```
FAILED: expected [lastCrawledAt to be null]
Actual: [2026-07-24T01:05:19.123456Z]
```
The field carried over from the previous test.

**After fix (both tests):**
```
Tests run: 100, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## What We Tried

1. **Wrapping test in `@Transactional`** — thought it would keep TX semantics clean. Made it worse: AFTER_COMMIT never fired. Removed the annotation entirely.

2. **Using `@WithMockUser` for auth** — expected the annotation to inject a user. It didn't work in `@SpringBootTest` context. Swapped to `user()` post-processor. Fixed immediately.

3. **Synchronous assertions after import** — thought if the request returned 200, JMS consumer had run. It hadn't. Added `Awaitility.await()` to block until the broadcast was verified. Stabilized the flaky test.

4. **Direct DB query for lastCrawledAt** — tried to query fresh from the DB each time. The problem was the in-memory `AtomicReference` in the service, not the DB. Changed to capture before-state and assert advancement instead of exact values.

## Root Cause Analysis

The root was **Spring Boot 4.x breaking changes bundled with proxy ordering assumptions**. Three things happened at once:

1. **Spring Boot 4.x reorganized test annotations** — old paths worked via re-exports, but IDEs and auto-complete pointed at the wrong place, creating silent import failures.

2. **Spring Security test helpers changed entry points** — `@WithMockUser` worked in sliced WebMvcTest contexts but not in full `@SpringBootTest`. The docs hadn't caught up; examples online still used the old pattern.

3. **TX semantics in integration tests conflicted with AFTER_COMMIT listeners** — wrapping tests in `@Transactional` rolls back the TX after the test, which suppresses AFTER_COMMIT events. The pattern worked fine for simpler tests, but broke the event-driven flow here.

The underlying lesson: **when integrating async pipelines (JMS, TX events, `@Async` proxies) in tests, the test TX itself becomes a critical piece of the wiring. Treating it casually (auto-wrapping with `@Transactional`) silently breaks the entire pipeline.**

## Lessons Learned

- **`@TransactionalEventListener(AFTER_COMMIT)` + test integration**: Never wrap an integration test in `@Transactional` if the code under test depends on AFTER_COMMIT events. Let each test TX commit normally; Spring's test context handles cleanup.

- **`@Async` (outer) + `@Transactional` (inner) proxy ordering is load-bearing**: When a method is both async and transactional, Spring creates nested proxies. The outer (`@Async`) submits to the thread pool; the inner TX commits before the future resolves. This ordering is why `allOf().join()` synchronizes correctly. Don't assume one waits for the other — measure and verify.

- **Awaitility is the right tool for JMS assertions**: Direct assertions after `mockMvc.perform()` will race. JMS consumer runs on a background thread; use `await()` to poll the assertion condition until it's true or timeout.

- **`SecurityMockMvcRequestPostProcessors.user()` in `@SpringBootTest`**: The `@WithMockUser` annotation doesn't work in full-context integration tests. Always use the post-processor version for per-request auth injection.

- **Singleton state in service classes can bleed across test methods**: Spring reuses the same context for all test methods in a class. In-memory state (like `AtomicReference<Instant>`) persists. Either reset it in `@BeforeEach` or design assertions to work with shared state (compare relative values, not absolutes).

- **Spring Boot 4.x imports matter**: A wrong import path can hide until the test runs, because old re-exports still compile. Always verify the actual class location, not the IDE autocomplete suggestion.

## Next Steps

1. **Add to memory for future projects** (DONE — this entry captures it)
2. **Check if D6-12 through D6-15 (final polish & packaging) are still in scope** — task breakdown shows them unchecked. If they're expected, they're the next item.
3. **Run `mvn clean package` to verify JAR builds and runs cleanly** — integration tests pass, but packaging sometimes reveals resource/config issues.
4. **Add a brief "Testing" section to `README.md`** — document how to run the test suite and check coverage locally.

## Files Committed

- `src/test/.../integration/ExcelImportIntegrationTest.java` (new, 152 lines)
- `src/test/.../integration/CrawlJobIntegrationTest.java` (new, 138 lines)
- `src/test/.../util/ExcelFixtureBuilder.java` (new, helper for in-memory Excel fixtures)
- `src/main/.../messaging/ImportStatsCache.java` (reset() made public for test teardown)
- `pom.xml` (Jacoco plugin added, 34 lines added)
- `docs/task-breakdown.md` (D6-09/10/11 marked done)
- `docs/e2e-testing-guide.md` (Section 6.7 added, coverage and integration test instructions)

**Commit**: 12c10a4, authored 2026-07-24 01:04

---

**Logged by**: journal-writer (subagent)  
**For**: social-analytics 6-day sprint wrap

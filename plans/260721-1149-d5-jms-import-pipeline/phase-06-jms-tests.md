# Phase 06 — Integration Test: Publish → Consume (D5-06)

**Status:** [x] completed

## Goal
Write `@SpringBootTest` integration tests that verify the full JMS flow using the embedded
Artemis broker (no Testcontainers needed).

## Files to Create
- `src/test/java/com/sunasterisk/socialanalytics/messaging/ImportJmsPipelineIT.java`

## Test Cases

### TC-01 — Publish → Consume (happy path)
1. Insert a seed `User` + `Post` via repositories
2. Trigger `ExcelImportService` or directly publish `ImportSucceededEvent` via `ApplicationEventPublisher`
3. Use `Awaitility` (or `Thread.sleep` fallback) to wait up to 5s for listener to run
4. Assert `importStatsCache.get()` is not null and `totalPosts >= 1`

### TC-02 — AFTER_COMMIT only
1. Inside a `@Transactional` method that ROLLS BACK, publish `ImportSucceededEvent`
2. Verify no JMS message is delivered (stats cache unchanged after 2s wait)

### TC-03 — DLQ on listener failure
1. Create a test listener that always throws `RuntimeException` on `IMPORT_COMPLETED`
2. Send a message directly via `JmsTemplate`
3. After Artemis exhausts retries, assert message appears in `DLQ.IMPORT_COMPLETED`
   via `jmsTemplate.receiveSelected("DLQ.IMPORT_COMPLETED", ...)`

## Dependencies
- `Awaitility` (already in spring-boot-starter-test transitively, or add explicitly)
- Embedded Artemis broker (activated by `spring-boot-starter-artemis` + `artemis-jakarta-server`)

## Success Criteria
- All 3 test cases pass
- No test uses mocked JMS — real embedded broker only

## Implementation Summary

**Completed:** D5-06

**Files Created:**
- `src/test/java/com/sunasterisk/socialanalytics/messaging/ImportJmsPipelineTest.java` — `@SpringBootTest` integration test with 3 test cases

**Files Modified:**
- `src/test/java/com/sunasterisk/socialanalytics/service/ExcelImportServiceTest.java` — added `@Mock ApplicationEventPublisher` to fix NullPointerException (M4 post-review fix)
- `src/main/resources/application-test.properties` — added Artemis config to test profile (M4 post-review fix)

**Test Cases (All Pass):**

1. **TC-01 (Happy Path):** Publishes `ImportSucceededEvent` → waits for listener with Awaitility (M3: `.during()` instead of `Thread.sleep()`) → asserts `importStatsCache.get()` is present and `totalPosts >= 1`

2. **TC-02 (AFTER_COMMIT Only):** Publishes event inside `@Transactional` that rolls back → verifies no JMS message delivered (cache unchanged after 2s wait) → confirms event published only on commit

3. **TC-03 (DLQ Accessibility):** Sends message directly via `JmsTemplate` with listener that always throws → after max retries, asserts message in `DLQ.IMPORT_COMPLETED` queue (H2 post-review: honest Javadoc on DLQ timing)

**Key Details:**
- All 64 tests pass (integration + unit)
- Uses real embedded Artemis broker; no mocks
- Awaitility for async assertions with 5s timeout
- Post-review fixes: exception boundary (H1), honest test docs (H2), Serializable removed (H3), per-platform query (M1), Optional return (M2), Awaitility `.during()` (M3), test profile config (M4)

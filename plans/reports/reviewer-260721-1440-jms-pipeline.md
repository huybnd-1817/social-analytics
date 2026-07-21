## Code Review Summary

### Scope
- Files reviewed: 10 source files + pom.xml + application.properties + application-test.properties
- LOC: ~400 production, ~180 test
- Focus: D5 JMS import pipeline (new files + ExcelImportService/PostRepository changes)

---

### Overall Assessment

Solid, well-scoped implementation. AFTER_COMMIT semantics are correct, the two-tier event indirection (Spring event → JMS) properly decouples the transactional write from the broker call. JSON converter is wired and `_type` header set. Code style matches the rest of the project (Lombok, SLF4J, `@RequiredArgsConstructor`). Three issues below warrant attention before shipping.

---

### High Priority

**H1 — `ImportEventListener.onImportCompleted` has no exception boundary; listener failures silently re-queue without a bound**

`@JmsListener` with `acknowledge-mode=auto` (JMS `AUTO_ACKNOWLEDGE`) means the broker ACKs the message *before* the listener body runs when using the default `SimpleMessageListenerContainer`. If the listener throws (e.g. `postRepository.count()` fails due to a transient DB error), the message is already ACKed and lost — it never reaches the DLQ. Conversely, if Spring's `DefaultMessageListenerContainer` is the configured factory (which Boot uses by default), it NAKs on exception and the broker retries indefinitely until the Artemis redelivery limit, but there is no `try/catch` in `onImportCompleted` to handle partial recalculation or emit a metric.

Recommendation: wrap the body in try/catch, log the error, and let it propagate so the container can NAK and route to DLQ normally — but add `spring.jms.listener.acknowledge-mode=auto` awareness comment, or switch to `client` with explicit ack, so the semantics are intentional and documented.

```java
@JmsListener(destination = JmsQueues.IMPORT_COMPLETED)
public void onImportCompleted(ImportCompletedMessage message) {
    try {
        // ... recalc
    } catch (Exception ex) {
        log.error("Stats recalculation failed for batchId={}: {}",
                message.batchId(), ex.getMessage(), ex);
        throw ex;  // re-throw so container NAKs → Artemis retries → DLQ
    }
}
```

**H2 — DLQ test (TC-03) is vacuous and does not prove real DLQ routing**

TC-03 sends a message *directly* to `DLQ.IMPORT_COMPLETED` and immediately receives it back. This does not test that a failing listener actually routes to DLQ. A real DLQ test would: (a) inject a listener that always throws, (b) send to the main queue, (c) await message appearance in `DLQ.IMPORT_COMPLETED`. As written, TC-03 proves nothing about DLQ behavior and gives false confidence. Either fix the test or document clearly that DLQ routing is untested.

**H3 — `ImportCompletedMessage` implements `Serializable` but converter is JSON; the interface is misleading and risky**

`MappingJackson2MessageConverter` serializes to JSON text — Java serialization is never used. The `implements Serializable` + `serialVersionUID` on the record implies it could be deserialized via `ObjectInputStream`, which would bypass the Jackson converter and reintroduce the classic Java-deserialization attack surface if a queue config ever regresses to using `ObjectMessage`. Drop `Serializable` entirely; the JSON converter does not require it.

---

### Medium Priority

**M1 — `countByPlatform` counts ALL posts regardless of status; inconsistent with existing duplicate-check scope**

`PostRepository.countByPlatform(SocialProvider)` counts every `Post` row for that platform, including `INACTIVE`/soft-deleted ones. The stats will diverge from what users see (active posts only). Should be `countByPlatformAndStatus(SocialProvider, PostStatus.ACTIVE)` to stay consistent with the partial unique index (`WHERE status = 'ACTIVE'`) and the existing `findByStatusAndPlatform...` queries.

**M2 — `ImportStatsCache.get()` returns `null` before first import; callers must null-check**

The cache is declared to return `null` and the Javadoc says so, but no consumer (controller or template) is visible yet. When this cache is eventually wired to an endpoint, forgetting the null check is a NPE waiting to happen. Consider returning `Optional<ImportStats>` or a static `ImportStats.EMPTY` sentinel to make the empty state explicit in the type.

**M3 — TC-02 uses `Thread.sleep(400)` instead of Awaitility**

TC-01 and TC-03 correctly use Awaitility for async assertions. TC-02 uses a raw 400 ms sleep, which is both slow on fast machines and flaky on slow CI agents. Since Awaitility is already on the classpath (used in TC-01), use `await().during(400, MILLISECONDS).until(() -> importStatsCache.get() == null)` or the equivalent for a consistent negative assertion.

**M4 — No `application-test.properties` override for Artemis mode**

`application-test.properties` configures H2 and disables Flyway/scheduler but does not set `spring.artemis.mode=embedded` explicitly for the test profile. The `@SpringBootTest` integration tests rely on the base `application.properties` value happening to be `embedded`. This works today but is fragile if base properties ever change. Add `spring.artemis.mode=embedded` + `spring.artemis.embedded.queues=IMPORT_COMPLETED` to `application-test.properties` to make the test profile self-contained.

---

### Low Priority

**L1 — `ImportStatsCache` lives in `messaging` package but has no messaging concern**

It is an application-level singleton cache that could outlive the JMS pipeline. Consider moving it to a `cache` or `service` sub-package for better cohesion, especially once a controller consumes it.

**L2 — Stats recalculation runs two separate `COUNT` queries (one per platform)**

At low volume this is fine, but if the `ImportEventListener` is ever called at higher frequency (batch crawl + import overlap) the two-count pattern means the `totalPosts` and per-platform counts can be momentarily inconsistent. A single `GROUP BY platform` query would be atomic and halve DB round-trips. Minor at current scale; worth noting for future growth.

---

### Edge Cases Found

- **Concurrent imports**: If two imports commit simultaneously, `AFTER_COMMIT` fires for both and two `ImportCompletedMessage` messages land on the queue. The listener runs them serially (default single-threaded `@JmsListener`), so the cache will correctly reflect the last recalculation. No race condition here given the `AtomicReference.set` is atomic.
- **Broker unavailable at publish time**: `ImportEventProducer` catches and logs the JmsException without re-throwing — correct behavior since the DB commit already happened. The stats cache will be stale until the next successful import, but no data loss occurs.
- **`batchId` could be null**: `ImportSucceededEvent.batchId()` and `ImportCompletedMessage.batchId()` are `Long` (nullable boxed type). If `batch.getId()` is null (e.g. `save()` is mocked and doesn't set an ID in tests), the log lines work but `ImportCompletedMessage(null, count)` is silently accepted. Not a bug in the current flow since Hibernate sets the ID on `save()`, but worth making the param `long` (primitive) to encode the invariant.

---

### Positive Observations

- AFTER_COMMIT phase is correctly chosen — phantom messages on rollback are impossible by design.
- `ImportEventProducer` catch block correctly swallows the exception post-commit rather than rolling back (there is nothing to roll back at that point); log at ERROR level is appropriate.
- `JmsQueues` constants class with private constructor is a clean, DRY pattern.
- `MappingJackson2MessageConverter` with `MessageType.TEXT` avoids the Java-serialization attack surface (partially undermined by H3 above).
- `idx_posts_platform` composite index `(platform, status)` in the migration means `countByPlatform` (and the corrected `countByPlatformAndStatus`) will be index-only scans — no seq-scan risk.
- Test TC-01 and TC-02 use a real embedded Artemis + H2 with no mocks, which provides genuine integration confidence for the AFTER_COMMIT path.

---

### Recommended Actions (priority order)

1. **[H2]** Fix TC-03 to test real DLQ routing via a poisoned-message scenario, or add a comment explicitly acknowledging DLQ routing is not covered.
2. **[H1]** Add try/catch + re-throw in `ImportEventListener.onImportCompleted` with a clear comment on NAK semantics.
3. **[H3]** Remove `implements Serializable` from `ImportCompletedMessage`.
4. **[M1]** Change `countByPlatform` to `countByPlatformAndStatus(SocialProvider, PostStatus)` and call it with `PostStatus.ACTIVE` in the listener.
5. **[M2]** Change `ImportStatsCache.get()` return type to `Optional<ImportStats>` now, before a controller consumes the null.
6. **[M3]** Replace `Thread.sleep(400)` in TC-02 with Awaitility.
7. **[M4]** Add Artemis properties to `application-test.properties`.

---

### Metrics

- Type Coverage: high — records + strong enum typing throughout; one nullable Long (`batchId`) that could be primitive
- Test Coverage: 3 JMS integration tests + 8 unit tests for ExcelImportService; DLQ path not meaningfully covered (see H2)
- Linting Issues: 0 visible compilation/style issues

### Unresolved Questions

- Is `ImportStatsCache` intended to be read by a REST endpoint or dashboard? If yes, `Optional` return (M2) should be addressed before that work starts.
- Is the redelivery count on the embedded Artemis broker left at its default (10 attempts)? No `broker.xml` or programmatic config found. Documenting or testing the actual redelivery behavior would close the DLQ gap flagged in H2.

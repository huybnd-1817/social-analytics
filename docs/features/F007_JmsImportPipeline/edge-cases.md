---
fcode: F007
status: draft
authored_by: takumi
created: 2026-07-21
lang: en
---

# F007_JmsImportPipeline — Edge Cases

| ID | Scenario | Expected Behavior |
|----|---------|-------------------|
| EC-01 | Import DB transaction rolls back | Event never published (AFTER_COMMIT fires only on success) |
| EC-02 | Listener throws on first delivery | Artemis redelivers up to 6 times; message lands in DLQ after exhaustion |
| EC-03 | Import succeeds but JMS send fails | Import HTTP response returns 200 (send failure logged, not rethrown) |
| EC-04 | Two imports in quick succession | Two messages enqueued independently; listener processes sequentially |
| EC-05 | Empty import batch (0 records) | Event still published (batchId valid, recordCount=0); stats recalculate correctly |
| EC-06 | Listener concurrently recalculates stats | AtomicReference in ImportStatsCache ensures visibility; no dirty read |
| EC-07 | Artemis broker not available at startup (non-embedded) | Application fails fast — acceptable for dev (embedded always available) |

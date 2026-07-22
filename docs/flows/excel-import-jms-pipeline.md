---
slug: excel-import-jms-pipeline
status: implemented
authored_by: rebuild-spec
created: 2026-07-21
lang: en
features: [F002, F007]
---

# Process Flow: Excel Import + JMS Stats Pipeline

## Overview

End-to-end flow from a user uploading an Excel file through to the in-memory stats cache being updated asynchronously. Spans two features: F002 (import) and F007 (JMS pipeline).

## Actors

| Actor | Type | Description |
|-------|------|-------------|
| User | Human | Authenticated social media manager |
| ImportController | System | HTTP entry point |
| ExcelImportService | System | Validates and persists import batch |
| ApplicationEventPublisher | System | Spring in-process event bus |
| ImportEventProducer | System | Transactional event → JMS bridge |
| Artemis Broker | Infrastructure | Embedded in-VM JMS broker |
| ImportEventListener | System | JMS consumer; stats recalculator |
| ImportStatsCache | System | Thread-safe in-memory stats store |

## Happy Path

```
User
  │
  │  POST /import-posts  (multipart .xlsx, max 10 MB)
  │
  ▼
ImportController
  │
  ├─► ExcelImportService.importFile()
  │       │
  │       ├─ Parse .xlsx rows via ExcelRowMapper (Reflection + @ExcelColumn)
  │       ├─ Validate all rows (validate-first: abort if any invalid)
  │       ├─ Check duplicates: PostRepository.findByStatusAndPlatformAndPlatformPostIdIn()
  │       ├─ Persist Posts + ImportBatch in a single DB transaction
  │       └─ Call ApplicationEventPublisher.publishEvent(ImportSucceededEvent)
  │
  ├─ DB transaction COMMITS
  │
  │  [AFTER_COMMIT — fires because @TransactionalEventListener]
  │
  ▼
ImportEventProducer.onImportSucceeded()
  │
  ├─ Convert ImportSucceededEvent → ImportCompletedMessage {batchId, recordCount}
  └─ JmsTemplate.convertAndSend("IMPORT_COMPLETED", message)
       │  [JSON/TextMessage via MappingJackson2MessageConverter]
       ▼
Artemis IMPORT_COMPLETED queue
       │
       ▼
ImportEventListener.onImportCompleted()
  │
  ├─ PostRepository.count()                             → totalPosts
  ├─ PostRepository.countByPlatformAndStatus(FACEBOOK, ACTIVE) → facebookCount
  ├─ PostRepository.countByPlatformAndStatus(TWITTER,  ACTIVE) → twitterCount
  └─ ImportStatsCache.update(ImportStats{totalPosts, perPlatform})
       │
       ▼
ImportStatsCache (AtomicReference<ImportStats>)
  └─ Updated; future readers see new snapshot

HTTP Response (ImportBatchResponse) already returned to User
  ← independent of JMS processing above
```

## Error Paths

### Invalid Rows in Excel

```
ExcelImportService.importFile()
  │
  └─ Validation fails on ≥1 row
       │
       ├─ Transaction rolled back (no Posts persisted)
       ├─ ImportBatch status = FAILED
       ├─ ApplicationEventPublisher.publishEvent() NOT called (AFTER_COMMIT guard)
       └─ HTTP 400 returned with error details
```

### DB Transaction Rollback (after event published but before commit)

```
ImportSucceededEvent published in-process
  │
  └─ Transaction rolls back (e.g. constraint violation)
       │
       ├─ @TransactionalEventListener(AFTER_COMMIT) does NOT fire
       └─ No JMS message sent — stats cache unchanged
```

### JMS Listener Exception (stats recalculation fails)

```
ImportEventListener.onImportCompleted()
  │
  └─ Exception thrown (e.g. DB connectivity blip)
       │
       ├─ log.error(...)
       ├─ Exception re-thrown → Spring JMS NAKs the message
       ├─ Artemis retries delivery (max-delivery-attempts, default 10)
       └─ After exhausted: message routed to DLQ.IMPORT_COMPLETED (auto-created)
            └─ HTTP response to User is unaffected (import already committed)
```

### JMS Send Failure (producer cannot reach broker)

```
ImportEventProducer.onImportSucceeded()
  │
  └─ JmsTemplate.convertAndSend() throws
       │
       ├─ log.error(...) 
       └─ Exception NOT re-thrown (import already committed; JMS is best-effort)
            └─ Stats cache not updated for this batch
```

## Sequence Diagram

```
User    Controller   ImportService   EventPub   Producer   Artemis   Listener   StatsCache
 │          │              │              │          │          │          │           │
 │─ POST ──►│              │              │          │          │          │           │
 │          │─ import() ──►│              │          │          │          │           │
 │          │              │─ validate ──►│          │          │          │           │
 │          │              │─ persist ───►│          │          │          │           │
 │          │              │─ publish ───►│          │          │          │           │
 │          │              │        COMMIT│          │          │          │           │
 │          │              │              │─ onEvent►│          │          │           │
 │          │              │              │          │─ send ──►│          │           │
 │◄─ 200 ──│              │              │          │          │─ deliver►│           │
 │          │              │              │          │          │          │─ update ──►│
```

## State Transitions

### ImportBatch Status
```
PENDING → PROCESSING → DONE    (all rows valid + persisted)
                    → FAILED   (validation error; no rows persisted)
```

### ImportStatsCache
```
Optional.empty()          (initial — no import completed yet)
  └─► ImportStats{...}   (after first successful import + JMS processing)
  └─► ImportStats{...}   (updated after each subsequent successful import)
```

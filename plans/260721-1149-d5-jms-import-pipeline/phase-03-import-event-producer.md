# Phase 03 — ImportEventProducer + ExcelImportService Wiring (D5-03)

**Status:** [x] completed

## Goal
Wire `ApplicationEventPublisher` into `ExcelImportService`; implement `ImportEventProducer`
that bridges the internal Spring event to JMS after DB transaction commits.

## Files to Create
- `src/main/java/com/sunasterisk/socialanalytics/messaging/ImportEventProducer.java`

## Files to Modify
- `src/main/java/com/sunasterisk/socialanalytics/service/ExcelImportService.java`

## Steps

1. **ExcelImportService** — add `ApplicationEventPublisher` field (via `@RequiredArgsConstructor`);
   in `persistSuccess()` after `importBatchService.save(batch)`:
   ```java
   eventPublisher.publishEvent(new ImportSucceededEvent(batch.getId(), batch.getSuccessRecords()));
   ```

2. **ImportEventProducer** — `@Component`:
   ```java
   @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
   public void onImportSucceeded(ImportSucceededEvent event) {
       // send to JMS after DB commit
   }
   ```
   - Inject `JmsTemplate`
   - Call `jmsTemplate.convertAndSend(JmsQueues.IMPORT_COMPLETED, new ImportCompletedMessage(...))`
   - Wrap send in try/catch: log error, do NOT rethrow (import already committed)

3. Compile check

## Success Criteria
- `ExcelImportService` compiles with new field
- `ImportEventProducer` compiles; `@TransactionalEventListener(AFTER_COMMIT)` present
- No circular dependency introduced

## Implementation Summary

**Completed:** D5-03

**Files Created:**
- `src/main/java/com/sunasterisk/socialanalytics/messaging/ImportEventProducer.java` — `@Component` with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`

**Files Modified:**
- `src/main/java/com/sunasterisk/socialanalytics/service/ExcelImportService.java` — added `ApplicationEventPublisher` field; calls `publishEvent(new ImportSucceededEvent(...))` in `persistSuccess()` after batch save

**Key Details:**
- Event published only after DB transaction commits (AFTER_COMMIT phase ensures transactional consistency)
- Producer sends via `JmsTemplate.convertAndSend()` with error logging but no rethrow (import already committed)
- Boundary establishes producer-side exception handling (H1 post-review fix)

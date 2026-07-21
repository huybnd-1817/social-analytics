# Phase 04 — ImportEventListener + ImportStatsCache (D5-04)

**Status:** [x] completed

## Goal
Implement JMS consumer that recalculates aggregated post stats after each import.

## Files to Create
- `src/main/java/com/sunasterisk/socialanalytics/messaging/ImportEventListener.java`
- `src/main/java/com/sunasterisk/socialanalytics/messaging/ImportStatsCache.java`

## Steps

1. **ImportStatsCache** — `@Component` holding latest stats in an `AtomicReference<ImportStats>`:
   ```java
   public record ImportStats(long totalPosts, Map<String, Long> perPlatform) {}
   ```
   - `update(ImportStats stats)` — atomic set
   - `get()` — returns current snapshot (nullable → callers handle empty)

2. **ImportEventListener** — `@Component`:
   ```java
   @JmsListener(destination = JmsQueues.IMPORT_COMPLETED)
   public void onImportCompleted(ImportCompletedMessage message) { ... }
   ```
   - Query `postRepository.count()` for `totalPosts`
   - Query per-platform: `postRepository.countByPlatform(SocialProvider.FACEBOOK)` and `TWITTER`
   - Build `ImportStats`; call `importStatsCache.update(stats)`
   - Log: `"Stats recalculated after batchId={}: total={}, facebook={}, twitter={}"`

3. Add `countByPlatform(SocialProvider platform)` to `PostRepository` if not present

4. Compile check

## Success Criteria
- `@JmsListener` on `IMPORT_COMPLETED` queue
- `ImportStatsCache` updated atomically after each message
- Compiles with no errors

## Implementation Summary

**Completed:** D5-04

**Files Created:**
- `src/main/java/com/sunasterisk/socialanalytics/messaging/ImportEventListener.java` — `@Component` with `@JmsListener(destination = JmsQueues.IMPORT_COMPLETED)`; recalculates stats and re-throws on exception for NAK→DLQ
- `src/main/java/com/sunasterisk/socialanalytics/messaging/ImportStatsCache.java` — `@Component` holding `AtomicReference<ImportStats>`; provides `Optional<ImportStats> get()` and `update(ImportStats)`

**Files Modified:**
- `src/main/java/com/sunasterisk/socialanalytics/repository/PostRepository.java` — added `countByPlatformAndStatus(SocialProvider platform, PostStatus status)` method (M1 post-review fix)

**Key Details:**
- Listener queries `PostRepository.countByPlatformAndStatus()` for per-platform ACTIVE counts
- Cache returns `Optional<ImportStats>` for null-safe access (M2 post-review fix)
- Exception in listener triggers redelivery; after max attempts, Artemis moves to DLQ
- Atomic reference ensures thread-safe stats updates

# Phase 01 — Async Config + Properties (D4-01, D4-02, D4-08)

## Context Links
- Spec: `spec/automated-crawl-job/technical-spec.md` (R1, R2, R7)
- Existing config beans: `src/main/java/.../config/{JpaAuditingConfig,OpenApiConfig,SecurityConfig}.java`

## Overview
- **Priority:** P1 (foundation — every later phase depends on it)
- **Status:** completed
- **Description:** Stand up the async execution layer: `@EnableAsync` + `@EnableScheduling`,
  a properties-driven `ThreadPoolTaskExecutor`, and an `AsyncUncaughtExceptionHandler`.

## Key Insights
- Config beans in this project are plain `@Configuration` classes in `config/` — follow that.
- `@EnableAsync`/`@EnableScheduling` MUST NOT go on the main app class (spec R1) — keeps
  the async concern isolated and testable.
- `AsyncConfig implements AsyncConfigurer` so `getAsyncExecutor()` returns the pool and
  `getAsyncUncaughtExceptionHandler()` returns the handler — one bean wires both.

## Requirements
- Functional: R1 (enable async+scheduling), R2 (externalized pool config), R7 (uncaught handler).
- Non-functional: bounded pool, backpressure via `CallerRunsPolicy` (no unbounded queue growth).

## Architecture
```
AsyncConfig (@Configuration, @EnableAsync, @EnableScheduling, implements AsyncConfigurer)
 ├─ getAsyncExecutor()                → ThreadPoolTaskExecutor (core/max/queue/prefix from props)
 └─ getAsyncUncaughtExceptionHandler()→ (method, params, ex) -> log.error(...)
```
Data flow: `@Async` methods (Phase 02) dispatch onto `getAsyncExecutor()`. Void-returning
`@Async` exceptions land in the handler; `CompletableFuture`-returning ones surface at `.join()`.

## Related Code Files
- **Create:** `src/main/java/com/sunasterisk/socialanalytics/config/AsyncConfig.java`
- **Modify:** `src/main/resources/application.properties` (append `app.async.*`, `app.crawler.rate-ms`)

## Implementation Steps
1. Create `AsyncConfig`:
   - Annotate `@Configuration`, `@EnableAsync`, `@EnableScheduling`.
   - `implements AsyncConfigurer`.
   - Bind props with `@Value`: `app.async.core-pool-size` (5), `max-pool-size` (10),
     `queue-capacity` (100), `thread-name-prefix` ("crawler-").
   - `@Override public Executor getAsyncExecutor()`: build `ThreadPoolTaskExecutor`,
     set core/max/queue/prefix, `setRejectedExecutionHandler(new CallerRunsPolicy())`,
     `initialize()`, return.
   - `@Override public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler()`:
     return lambda logging `ERROR` with method name, args, exception (Slf4j).
2. Append to `application.properties` under a new `# D4: Async / Crawler` block:
   ```
   app.async.core-pool-size=5
   app.async.max-pool-size=10
   app.async.queue-capacity=100
   app.async.thread-name-prefix=crawler-
   app.crawler.rate-ms=3600000
   ```
   (`app.crawler.rate-ms` consumed in Phase 03; declared here to keep the config block whole.)

## Todo List
- [ ] Create `AsyncConfig` with pool bean + uncaught handler
- [ ] `@EnableAsync` + `@EnableScheduling` on the config (not app class)
- [ ] Append `app.async.*` + `app.crawler.rate-ms` to properties
- [ ] `mvn -q compile` clean

## Success Criteria
- App boots without bean errors; log shows executor init on startup.
- No `@EnableAsync`/`@EnableScheduling` on `SocialAnalyticsApplication`.

## Risk Assessment
| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Wrong `Executor` import (`java.util.concurrent` vs Spring) | Med | Low | Return `ThreadPoolTaskExecutor` as `Executor`; compile catches it |
| `CallerRunsPolicy` blocks scheduler thread under saturation | Low | Med | Acceptable at demo scale; queue=100 absorbs bursts |

## Security Considerations
- None new. No user data touched.

## Next Steps
- Unblocks Phase 02 (crawler `@Async` runs on this pool) and Phase 03 (scheduler enabled).

## Rollback
- Delete `AsyncConfig.java`; remove the properties block. No runtime state to unwind.

# Phase 04 — Last-Updated API + Dashboard UI (D4-06, D4-07)

## Context Links
- Spec: `spec/automated-crawl-job/technical-spec.md` (R8, R9, R10; US-D4-02, US-D4-03)
- Depends on: Phase 03 (`CrawlJobService.getLastCrawledAt()`)
- Existing: `controller/MetricController.java`, `controller/DashboardController.java`,
  `templates/dashboard.html`, `dto/MetricResponse.java` (record + static `from` pattern)

## Overview
- **Priority:** P2
- **Status:** completed
- **Description:** Expose last-crawl timestamp two ways: `GET /metrics/last-updated`
  (JSON) and a "Last Updated" row on the Thymeleaf dashboard.

## Key Insights
- DTOs here are Java `record`s (see `MetricResponse`). `LastUpdatedResponse` is a
  single-field record; `lastCrawledAt` nullable → serializes as JSON `null` (R8).
- `MetricController` is `@RestController @RequestMapping("/metrics")` — add a sibling
  `@GetMapping("/last-updated")`. Inject `CrawlJobService` alongside `MetricService`.
- `DashboardController` builds the model; add `lastCrawledAt` attribute (nullable).
- Template renders "Last crawled: {ts}" or "Never" — mirror existing `th:if` null pattern
  used for the Email row.
- Both endpoints already fall under `SecurityConfig`'s `anyRequest().authenticated()` — no
  security-config change needed (spec security note).

## Requirements
- Functional: R8 (JSON endpoint), R9 (model attribute), R10 (template row).
- Non-functional: no new security rule; reuse existing auth.

## Architecture
```
GET /metrics/last-updated
  MetricController → crawlJobService.getLastCrawledAt() → LastUpdatedResponse(instant?) → JSON

GET /  (dashboard)
  DashboardController → model.addAttribute("lastCrawledAt", crawlJobService.getLastCrawledAt())
  dashboard.html → row: th:if non-null → "Last crawled: {ts}" ; th:if null → "Never"
```
Input: `getLastCrawledAt()` (Instant or null). Output: JSON body / rendered HTML row.

## Related Code Files
- **Create:** `src/main/java/com/sunasterisk/socialanalytics/dto/LastUpdatedResponse.java`
- **Modify:** `controller/MetricController.java` (inject `CrawlJobService`, add endpoint)
- **Modify:** `controller/DashboardController.java` (inject `CrawlJobService`, add attribute)
- **Modify:** `templates/dashboard.html` (add "Last Updated" table row)

## Implementation Steps
1. `LastUpdatedResponse`: `public record LastUpdatedResponse(Instant lastCrawledAt) {}`.
2. `MetricController`: add `private final CrawlJobService crawlJobService;` (constructor via
   `@RequiredArgsConstructor` already present). Add:
   ```java
   @GetMapping("/last-updated")
   @Operation(summary = "Timestamp of the last completed crawl job")
   public LastUpdatedResponse lastUpdated() {
       return new LastUpdatedResponse(crawlJobService.getLastCrawledAt());
   }
   ```
3. `DashboardController`: inject `CrawlJobService` (add constructor / field — currently no
   deps, so add `private final CrawlJobService crawlJobService;` + `@RequiredArgsConstructor`
   OR explicit constructor). In `dashboard(...)`:
   `model.addAttribute("lastCrawledAt", crawlJobService.getLastCrawledAt());`.
4. `dashboard.html`: add a row after Provider in `.info-table`:
   ```html
   <tr>
     <td>Last Updated</td>
     <td th:if="${lastCrawledAt != null}" th:text="'Last crawled: ' + ${lastCrawledAt}">…</td>
     <td th:if="${lastCrawledAt == null}" class="na">Never</td>
   </tr>
   ```

## Todo List
- [ ] Create `LastUpdatedResponse` record
- [ ] Add `/last-updated` endpoint to `MetricController`
- [ ] Inject `CrawlJobService` into `DashboardController`, add model attribute
- [ ] Add "Last Updated" row to `dashboard.html`
- [ ] `mvn -q compile` clean

## Success Criteria
- `GET /metrics/last-updated` → `{"lastCrawledAt":"…"}` or `{"lastCrawledAt":null}`.
- Dashboard shows "Last crawled: …" post-run, "Never" before.
- Swagger lists the new operation under Metrics.

## Risk Assessment
| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| `DashboardController` lacks a constructor for injection | Med | Low | Add `@RequiredArgsConstructor` or explicit ctor |
| Instant renders as raw ISO in template (not localized) | Med | Low | Acceptable per spec (raw ISO-8601); formatting deferred |
| Endpoint accessible unauthenticated | Low | Med | Covered by existing `anyRequest().authenticated()` |

## Security Considerations
- Timestamp only — no sensitive data. Auth inherited from D3 `SecurityConfig`.

## Next Steps
- Feature complete after this + Phase 05 tests. Consider docs/changelog update.

## Rollback
- Remove endpoint + DTO; revert `DashboardController` to no-dep; drop template row.

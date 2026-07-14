---
title: "Phase 03 — Excel Export Service & Endpoint"
feature: F003
tasks: [D2-09, D2-10]
status: completed
priority: P1
effort: 3h
---

# Phase 03 — Excel Export Service & Endpoint

## Context Links

- Export spec (all FR/BR/ALG): `spec/excel-export-report/technical-spec.md`
- Edge cases: `spec/excel-export-report/edge-cases.md`
- Study §3 (findTop1 exists, N+1 gap), §8 risk #4

## Overview

**Priority:** P1 · **Status:** completed · **Depends on:** Phase 01 · **Parallel with:** Phase 02

Implement `GET /export-report`: fetch all ACTIVE posts, resolve latest metric per post, stream a
timestamped `.xlsx` via a reflection-based generic row writer. No DB write; idempotent.

## Key Insights

- **ACTIVE-only, unbounded fetch (BR-001):** use the Phase-01 `findByStatus(ACTIVE)` list overload.
- **Latest metric via existing `findTop1ByPostOrderByCrawledAtDesc` (BR-002):** N+1 accepted at
  demo scale (composite index keeps each lookup fast). Do NOT add a bulk query — YAGNI.
- **Empty-metric fallback (BR-002):** no metric → zero/empty counter cells; row never omitted.
- **Timestamped filename (BR-003):** `report_yyyyMMddHHmmss.xlsx` via `Content-Disposition: attachment`.
- **Reflection row writer (ALG-001):** derive headers + cells from `ExportRowModel` declared fields;
  `Number`→numeric cell, else `toString()`/empty. Split into `ReflectionRowWriter` util to keep
  `ExcelExportService` < 200 lines. **Do NOT build a `@ExcelColumn` annotation — deferred to D6.**
- **Date cells as strings** `yyyy-MM-dd HH:mm:ss` UTC (`publishedAt`, `crawledAt`); no native Excel dates.
- Stream to `HttpServletResponse` output stream; MIME
  `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.

## Requirements

FR-001..FR-005; BR-001..BR-003; ALG-001; verifies SC-001..SC-005.

## Related Code Files

**Create:**
- `controller/ExcelExportController.java` — `GET /export-report`, sets headers, streams workbook.
- `service/ExcelExportService.java` — fetch active posts → resolve latest metric → build rows → write workbook (< 200 lines).
- `util/ReflectionRowWriter.java` — generic header + row writer via `Class.getDeclaredFields()` (ALG-001).
- `dto/ExportRowModel.java` — flat POJO; field declaration order defines column order.

**Modify:** none (repository method added in Phase 01; export uses existing
`SocialMetricRepository.findTop1ByPostOrderByCrawledAtDesc`).

## Implementation Steps

1. `ExportRowModel` — fields in schema order: `platform, platformPostId, title, postUrl,
   publishedAt, status, likesCount, sharesCount, commentsCount, followersCount, reach,
   impressions, crawledAt` (dates as pre-formatted UTC strings).
2. `ReflectionRowWriter` (ALG-001): build header row from field names once; per instance write
   cells — `Number`→`setCellValue(double)`, else string/empty for null.
3. `ExcelExportService`:
   - `postRepository.findByStatus(ACTIVE)` (Phase-01 list overload).
   - Per post: `findTop1ByPostOrderByCrawledAtDesc(post).orElse(null)`; build `ExportRowModel`
     (null metric → zero counters, empty `crawledAt`). Format instants UTC `yyyy-MM-dd HH:mm:ss`.
   - Create `XSSFWorkbook` (SXSSF optional — implementer choice), sheet `"Report"`, write via writer.
4. `ExcelExportController.exportReport`: set `Content-Type` + `Content-Disposition` timestamped
   filename (BR-003); write workbook to response output stream; close workbook.
5. `./mvnw -q compile`.

## Todo List

- [x] `ExportRowModel` POJO (field order = column order)
- [x] `ReflectionRowWriter` util — ALG-001
- [x] `ExcelExportService` (active fetch, latest metric, zero fallback, UTC format) — BR-001,002
- [x] `ExcelExportController` `GET /export-report` (headers + stream) — BR-003, FR-001
- [x] `./mvnw -q compile` passes

## Success Criteria

- SC-001: 200, correct MIME + `Content-Disposition` attachment.
- SC-002: valid `.xlsx`; header row = `ExportRowModel` field names in order.
- SC-003: only ACTIVE posts; no DELETED.
- SC-004: post w/o metric present with 0/empty cells (not omitted).
- SC-005: filename contains `\d{14}` timestamp.
- Service file < 200 lines.

## Risk Assessment

| Risk | Likelihood | Impact | Countermove |
|------|-----------|--------|-------------|
| `getDeclaredFields()` order non-deterministic off HotSpot | Low | Med | HotSpot Java 26 deterministic; D6 `@ExcelColumn` planned; note in edge-cases |
| OOM on very large export (XSSF in-heap) | Low | Med | Demo scale acceptable; switch to SXSSF if needed |
| N+1 latency on large post count | Med | Low | Accepted per resolved decision; composite index mitigates |
| Workbook/stream not closed → leak | Low | Med | try-with-resources around workbook + output stream |

## Security Considerations

- No auth at D2 (deferred to D3). Endpoint open by design. Read-only, no data written.

## Rollback

Delete the 4 new files. No schema/data change; fully reversible.

## Next Steps

No downstream dependency. Independent of Phase 02 — can run concurrently.

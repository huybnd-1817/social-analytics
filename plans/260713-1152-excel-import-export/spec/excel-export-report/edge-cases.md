---
status: draft
authored_by: takumi
created: 2026-07-13
lang: en
---

# Edge Cases — Excel Export Posts & Metrics Report

| Scenario | What Happens | User-Facing Message |
|----------|--------------|---------------------|
| No ACTIVE posts in the database | Service returns an empty workbook: header row only, zero data rows. HTTP 200 is still returned; the file is valid and openable. | None — the downloaded file opens with only the header row. |
| Post exists but has no SocialMetric rows (never crawled) | The post is included in the sheet; all metric counter columns (`likes`, `shares`, `comments`, `followers`, `reach`, `impressions`, `crawledAt`) are written as `0` or empty string. Row is never omitted. | None — the file contains the row with blank metric cells. |
| Metric repository returns multiple snapshots for one post | `findTop1ByPostOrderByCrawledAtDesc` selects only the most recent record. Earlier snapshots are silently ignored. Result is always a single metric row per post. | None — only the latest snapshot appears in the file. |
| `Class.getDeclaredFields()` returns fields in unexpected order (non-HotSpot JVM) | Header and data cells may be misaligned if the JVM does not guarantee declaration order. At D2 / HotSpot Java 26 this is deterministic; on other JVMs alignment breaks silently. | None at D2 — noted as a future risk; `@ExcelColumn(order=N)` annotation planned for D6. |
| Very large number of ACTIVE posts (e.g. > 100 k rows) | SXSSF streaming writer flushes rows to disk incrementally, avoiding heap exhaustion. If XSSF is chosen instead, the full workbook lives in heap — OutOfMemoryError is possible under high load. | None at D2 demo scale. If OOM occurs the connection drops with no user-readable message; mitigate by choosing SXSSF. |
| POI library not present on classpath (`NoClassDefFoundError`) | Spring fails to start; the endpoint is never registered. Build breaks at startup. | Server returns no response (connection refused). Fix: ensure `poi-ooxml 5.3.0` is declared in `pom.xml` (D2-06 dependency task). |
| `publishedAt` or `crawledAt` is `null` | Reflection writer checks for `null`; writes empty string cell. No `NullPointerException`. | None — the cell is blank in the downloaded file. |

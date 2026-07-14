---
status: draft
authored_by: takumi
created: 2026-07-13
lang: en
---

# Edge Cases — Excel Bulk Import Posts

| Scenario | What Happens | User-Facing Message |
|----------|--------------|---------------------|
| File is 0 bytes or completely empty | Service rejects before creating any ImportBatch; no DB writes | "File contains no data rows" (HTTP 400) |
| File has header row only — no data rows | Service detects empty sheet body; no DB writes, no ImportBatch created | "File contains no data rows" (HTTP 400) |
| Required columns (`platform`, `platform_post_id`) missing from header | Service rejects during structure validation before row loop; no DB writes | "Missing required columns: platform, platform_post_id" (HTTP 400) |
| One or more rows have `platform` set to an unknown value (e.g., `INSTAGRAM`) | Full validation pass completes; batch marked FAILED, zero posts written, ImportBatch record persisted | ImportBatchResponse: status=FAILED, successRecords=0, failedRecords=N (HTTP 200) |
| One or more rows have blank `platform_post_id` | Full validation pass completes; batch FAILED, zero posts written | ImportBatchResponse: status=FAILED, successRecords=0, failedRecords=N (HTTP 200) |
| Two rows in the same file share the same `(platform, platform_post_id)` pair | In-file duplicate detected during validation pass; batch FAILED, zero posts written | ImportBatchResponse: status=FAILED, successRecords=0, failedRecords=N (HTTP 200) |
| A row's `(platform, platform_post_id)` matches an existing ACTIVE post in the database | DB duplicate detected during validation pass; batch FAILED, zero posts written | ImportBatchResponse: status=FAILED, successRecords=0, failedRecords=N (HTTP 200) |
| A row whose `(platform, platform_post_id)` matches a DELETED (soft-deleted) post | Partial unique index applies only to ACTIVE rows; re-import is allowed; row treated as valid | No error for this row; contributes to successRecords if rest of file is valid |
| `published_at` cell is present but unparseable (not ISO-8601 and not a date-type cell) | Row fails validation; batch FAILED, zero posts written | ImportBatchResponse: status=FAILED, failedRecords=N (HTTP 200) |
| File exceeds 10 MB multipart limit | Spring multipart filter rejects the request before it reaches the controller | "Maximum upload size exceeded" (HTTP 400 or 413 depending on Spring Boot version) |
| No seed user exists in the database | Service throws internal error before any validation; no ImportBatch created | "Internal server error" (HTTP 500) — indicates misconfigured environment |
| File is valid `.xlsx` but contains 0 valid rows after a mixed valid/invalid pass | All-or-nothing: any invalid row → entire batch FAILED | ImportBatchResponse: status=FAILED, successRecords=0 (HTTP 200) |

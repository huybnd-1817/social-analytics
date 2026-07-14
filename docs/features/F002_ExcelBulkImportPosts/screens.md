---
status: draft
authored_by: takumi
created: 2026-07-13
lang: en
---

# Screens — Excel Bulk Import Posts

## Screen List

N/A — background feature; no user-facing screens.

## User Journey

API-level journey (no UI at D2 scope):

1. Caller sends `POST /import-posts` with `Content-Type: multipart/form-data` and field `file` containing the `.xlsx` upload.
2. Service validates file structure (non-empty, required columns present); rejects with HTTP 400 if malformed.
3. Service runs the full validation pass over every data row (platform enum, required fields, uniqueness checks); no database writes occur during this pass.
4. If any row is invalid: service persists a FAILED `ImportBatch` audit record (zero posts written) and returns HTTP 200 with `ImportBatchResponse` showing `status=FAILED`, `successRecords=0`, `failedRecords=N`.
5. If all rows are valid: service persists all posts and a DONE `ImportBatch` in a single transaction and returns HTTP 200 with `ImportBatchResponse` showing `status=DONE`, `successRecords=N`, `failedRecords=0`.

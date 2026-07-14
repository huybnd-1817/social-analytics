---
title: "Phase 02 — Excel Import Service, Endpoint & Exception Handling"
feature: F002
tasks: [D2-07, D2-08]
status: completed
priority: P1
effort: 4h
---

# Phase 02 — Excel Import Service, Endpoint & Exception Handling

## Context Links

- Import spec (all FR/BR/SM/ALG): `spec/excel-bulk-import-posts/technical-spec.md`
- Edge cases: `spec/excel-bulk-import-posts/edge-cases.md`
- Study §5 (GlobalExceptionHandler gap), §8 risks #3, #5, #6, #7

## Overview

**Priority:** P1 · **Status:** completed · **Depends on:** Phase 01

Implement `POST /import-posts`: parse `.xlsx`, validate every row first (zero DB writes),
then all-or-nothing persist. Always persist the `ImportBatch` audit row. Return counts-only
`ImportBatchResponse`. Wire the missing exception mappings.

## Key Insights

- **Validate-first / all-or-nothing (BR-004, SM-001):** full validation pass builds an error
  list; if ANY error → batch FAILED, `successRecords=0`, `failedRecords=totalRecords`, zero
  posts persisted. Only an empty error list → persist all posts in one transaction, batch DONE.
- **Seed-user fallback (BR-005):** resolve owning user via `userRepository.findFirstByOrderByIdAsc()`
  (add this method). Missing → `IllegalStateException` → 500 (misconfig).
- **Header matching case-insensitive; enum values case-sensitive** (`FACEBOOK`/`TWITTER`).
- **`published_at` parsed as UTC** (consistent with `hibernate.jdbc.time_zone=UTC`); unparseable → row error.
- **Reject non-`.xlsx`** (by extension/content-type) with 400 before POI parse.
- **Split the row mapper out** (`ExcelRowMapper` util) so `ExcelImportService` stays < 200 lines.
- **`ImportBatch.importedAt`** set by `@CreatedDate` on INSERT, overwritten by service on DONE.
- **NAMED_ENUM + H2 incompatible** → this code must be testable via pure Mockito (Phase 04).

## Requirements

FR-001..FR-008; BR-001..BR-006; SM-001; ALG-001; verifies SC-001..SC-004.

## Related Code Files

**Create:**
- `controller/ImportController.java` — `POST /import-posts`, `@RequestParam("file") MultipartFile`, `@Tag`/`@Operation`.
- `service/ExcelImportService.java` — structure guard, validate loop, all-or-nothing persist (< 200 lines).
- `service/ImportBatchService.java` — seed-user resolution + batch save (`@Transactional`).
- `util/ExcelRowMapper.java` — header index build, cell→field mapping, UTC `published_at` parse (ALG-001).
- `dto/ImportBatchResponse.java` — `record` + `from(ImportBatch)` factory (counts + status).

**Modify:**
- `repository/UserRepository.java` — add `Optional<User> findFirstByOrderByIdAsc()`.
- `controller/GlobalExceptionHandler.java` — add `IllegalArgumentException` → 400 `{"error":...}`,
  `IllegalStateException` → 500, `MaxUploadSizeExceededException` → 400.

## Implementation Steps

1. `ImportBatchResponse` record: `totalRecords, successRecords, failedRecords, status` + `from(entity)`.
2. `UserRepository.findFirstByOrderByIdAsc()`.
3. `ExcelRowMapper` (ALG-001): build header index case-insensitively; require headers
   `platform`, `platform_post_id` present (else `IllegalArgumentException` "Missing required columns");
   map each data row to an unvalidated `Post`; parse `published_at` UTC; return rows + parse errors.
4. `ExcelImportService`:
   - Guard: reject empty file / non-`.xlsx` / no data rows → `IllegalArgumentException` (BR-006, FR-006).
   - Resolve seed user (via `ImportBatchService`); create PENDING→PROCESSING batch.
   - Validate loop: required cells (BR-001), platform enum (BR-002), in-file + in-DB active dup
     via the Phase-01 repo method (BR-003), parse errors from mapper.
   - Empty errors → persist posts + batch DONE in single tx (BR-004). Else → batch FAILED, 0 posts.
   - Log row-level errors; return `ImportBatchResponse.from(batch)`.
5. `ImportController.importPosts` → HTTP 200 with `ImportBatchResponse` in BOTH outcomes.
6. Add exception handlers to `GlobalExceptionHandler`.
7. `./mvnw -q compile`.

## Todo List

- [x] `ImportBatchResponse` record + `from`
- [x] `UserRepository.findFirstByOrderByIdAsc`
- [x] `ExcelRowMapper` (header index, cell map, UTC parse) — ALG-001
- [x] `ImportBatchService` (seed user + save)
- [x] `ExcelImportService` (guard, validate, all-or-nothing persist) — BR-001..006, SM-001
- [x] `ImportController` `POST /import-posts`
- [x] `GlobalExceptionHandler`: IllegalArgumentException/IllegalState/MaxUploadSize
- [x] `./mvnw -q compile` passes

## Success Criteria

- SC-001: valid 3-row → 200, DONE, success=3, 3 Post rows.
- SC-002: invalid platform → 200, FAILED, success=0, 0 posts, batch persisted.
- SC-003: empty file → 400 `{"error":...}`, 0 batches.
- SC-004: in-DB active dup → 200, FAILED, 0 posts.
- All service files < 200 lines.

## Risk Assessment

| Risk | Likelihood | Impact | Countermove |
|------|-----------|--------|-------------|
| `ExcelImportService` exceeds 200 lines | Med | Med | Row mapping already split to `ExcelRowMapper`; extract validators too if needed |
| POI numeric-vs-string cell for `published_at` | Med | Med | Mapper handles both numeric-date and ISO-string; unparseable → row error |
| Dup check race (in-DB) between validate and persist | Low | Low | Partial unique index is DB backstop → `DataIntegrityViolationException` → 409 |
| Seed user absent | Low | High | `IllegalStateException` → 500; documented as env misconfig |

## Security Considerations

- No auth at D2 (deferred to D3/OAuth2). Endpoint open by design.
- Do not leak POI/stack details to client — handlers return generic `{"error":...}`.
- Multipart size cap (Phase 01) bounds upload DoS surface.

## Rollback

Delete the 5 new files; revert `UserRepository` + `GlobalExceptionHandler` edits. No schema change
(entities/tables already exist). No data written on failure path.

## Next Steps

Phase 04 unit-tests this service. Phase 03 independent (parallel-safe).

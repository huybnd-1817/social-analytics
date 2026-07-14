# Adversarial Code Review — Excel Import/Export Feature

**Branch:** feature/export-import-excel  
**Scope:** ExcelImportService, ImportBatchService, ExcelExportService, ExcelRowMapper, ReflectionRowWriter, ImportController, ExcelExportController, GlobalExceptionHandler (+3 handlers), PostRepository (+2), UserRepository (+1), pom.xml (poi-ooxml 5.3.0), application.properties

---

## F1 — MEDIUM | Internal State Leakage via IllegalStateException Response Body

**Location:** `GlobalExceptionHandler.java:67-68`

**Problem:** The `handleInternalState` handler concatenates `ex.getMessage()` directly into the HTTP response body:
```
"Internal server error: " + ex.getMessage()
```
This leaks internal implementation detail to any caller.

**Trigger paths:**
1. DB has no users → `IllegalStateException("No seed user found — environment misconfigured (no User row in DB)")` → exposed verbatim in response.
2. Any `IllegalStateException` from POI internals (e.g., formula cells in header — see F2 below) → POI message exposed.
3. `IllegalStateException` from `ReflectionRowWriter.writeRow` or `ExcelRowMapper.mapRows` → exposed.

**Impact:** Information disclosure. Reveals DB configuration state, internal class/method names, and POI version-specific messages to unauthenticated callers. Severity increases when auth arrives on D3 if this pattern is not fixed first.

**Fix:** Return a generic "Internal server error" body. Log `ex.getMessage()` at ERROR; never echo it to the response.

---

## F2 — MEDIUM | FORMULA Cell in Header Row Throws Unhandled IllegalStateException → 500

**Location:** `ExcelRowMapper.java:73`

```java
String header = cell.getStringCellValue().trim().toLowerCase();
```

**Problem:** `getStringCellValue()` is called with no prior cell-type guard. In Apache POI XSSF, if `cellType == FORMULA` and the cached formula result is NUMERIC, BOOLEAN, or ERROR, this throws `IllegalStateException` with message like `"Cannot get a STRING value from a NUMERIC formula cell"`. The exception propagates uncaught through `buildHeaderIndex` → `importPosts` (not caught by `catch (IOException e)`) → `GlobalExceptionHandler.handleInternalState` → HTTP 500 + POI internal message exposed.

**Trigger:** Send a `.xlsx` file where any header cell contains a formula (e.g., `=CONCATENATE("plat","form")`).

**Impact:** Incorrect HTTP status (500 instead of 400 for bad input) + internal POI message leakage. Combined with F1.

**Fix:** Before calling `getStringCellValue()` in `buildHeaderIndex`, check `cell.getCellType()`. If not `STRING` (or `BLANK`), treat the cell as having an empty/unparseable header name.

---

## F3 — MEDIUM | FORMULA Data Cells Silently Return null — Misleading Validation Error

**Location:** `ExcelRowMapper.java:182–190` (`getCellString` switch statement)

**Problem:** `getCellType() == FORMULA` falls to `default -> null` in the switch. A data cell containing a formula (e.g., `=A2&"-post"` for `platform_post_id`) silently returns `null`. The resulting `Post` entity has `platform_post_id = null`, which then generates a misleading validation error "platform_post_id is required" with the wrong row number (see F5). The user cannot know it was a formula cell that caused the problem.

**Trigger:** Upload a file where any data cell uses a formula.

**Impact:** Silent data loss; user gets a generic "field required" error with no actionable hint. Not a security issue but a correctness failure that blocks legitimate use.

**Fix:** Handle `FORMULA` case in `getCellString` by evaluating the cached result type (`cell.getCachedFormulaResultType()`) and extracting the appropriate value, or convert via a `FormulaEvaluator`.

---

## F4 — MEDIUM | Audit Batch Not Persisted When IOException or DataIntegrityViolationException Occurs After Batch Creation

**Location:** `ExcelImportService.java:69, 89`

**Problem:** `createProcessingBatch` is annotated `@Transactional` with default `REQUIRED` propagation. It **joins** the outer `@Transactional` transaction of `importPosts`. Two failure modes destroy the audit record:

1. **IOException path:** `file.getInputStream()` / `XSSFWorkbook` parse throws `IOException` (line 89) → re-thrown as `IllegalArgumentException` (RuntimeException) → outer `@Transactional` marks the transaction for rollback → the batch `save()` from line 69 is rolled back with it. The audit record that "must always be persisted" does not survive.

2. **Concurrent import race (DataIntegrityViolationException):** Two concurrent imports of the same file pass `checkDbDuplicates` simultaneously (no row-level lock), then both call `saveAll`. The second hits the partial unique index `uk_platform_post_active` → `DataIntegrityViolationException` → outer tx rolls back → batch lost, 409 response with no traceable batch ID.

The code comment on line 68–69 states: *"audit record tồn tại kể cả khi validate thất bại"* — this claim is false for the IOException and concurrent-race cases.

**Impact:** Missing audit records make incident investigation impossible. The advertised all-or-nothing guarantee is broken asymmetrically.

**Fix:** Use `REQUIRES_NEW` propagation on `createProcessingBatch` so the batch persists in an independent transaction that commits before the outer tx can roll back. For the race, add `@Transactional(noRollbackFor = DataIntegrityViolationException.class)` or handle the DIVE inside the service and return a FAILED batch.

---

## F5 — LOW | Wrong Row Numbers in Validation Error Messages

**Location:** `ExcelImportService.java:107`, `ExcelImportService.java:158`

**Problem:** `validateRows` iterates `parsedPosts` (a list of only successfully-mapped, non-blank rows) and computes `displayRow = i + 2` treating index `i` as if it maps directly to sheet row `i+1`. If any earlier rows were skipped (blank rows) or produced parse errors in `mapRows`, the index into `parsedPosts` no longer matches the original sheet row number. The same bug exists in `checkDbDuplicates` at `i + 2`.

**Example:** Sheet rows 2, 3 valid; row 4 blank (skipped); row 5 valid with duplicate key. `parsedPosts = [row2, row3, row5]`. `checkDbDuplicates` reports duplicate at row `2 + 2 = 4`, but the actual row is row 5. User edits row 4 (which is blank) and cannot find the problem.

**Impact:** Users receive wrong row numbers in error messages, making it impossible to fix large import files without manual counting.

**Fix:** Store the original sheet row number inside `Post` (a transient field, or alongside it in a tuple/record) when populating `parsedPosts` in `mapRows`. Use that stored row number in all error reporting.

---

## F6 — LOW | Memory Exhaustion via Compressed xlsx (OOM Risk)

**Location:** `ExcelImportService.java:54`, `ExcelExportService.java:65`

**Problem:** `XSSFWorkbook` (in-heap) is used for both import and export. For import, a 10MB `.xlsx` file accepted by `max-file-size=10MB` can contain heavily compressed XML that expands to ~100–200MB in heap (POI's `ZipSecureFile` defaults allow up to ~100x expansion ratio per entry, with no absolute per-entry cap below ~4GB). Under JVM defaults (256–512MB heap for a typical Spring Boot process) a single crafted upload can trigger OOM or severe GC pressure.

**Trigger:** Craft a valid `.xlsx` with a single sheet containing ~50k rows with long string content, compressed to under 10MB.

**Impact:** Service OOM / availability denial (single request, no auth required at D2).

**Fix:** Lower `ZipSecureFile.setMinInflateRatio` / `setMaxEntrySize` at application startup, or add a row-count limit in `mapRows` (e.g., reject files with >10k rows) before the full heap allocation occurs.

---

## F7 — LOW | filename Not Length-Validated Before DB Persist

**Location:** `ExcelImportService.java:65–66`

**Problem:** `file.getOriginalFilename()` is persisted as `ImportBatch.fileName` without a length check. The DB column is `VARCHAR(255)`. A client that sends a multipart upload with a `filename` parameter > 255 characters causes `DataIntegrityViolationException` → HTTP 409 "Data conflict". No batch record is created (tx rolls back), but the response code and message are misleading — the caller sees "uniqueness or reference constraint" violation rather than "filename too long".

**Trigger:** POST to `/import-posts` with `Content-Disposition: form-data; name="file"; filename="[256+ chars].xlsx"`.

**Impact:** Wrong HTTP status (409 instead of 400), confusing error message. Minor but observable from the outside.

**Fix:** Truncate or reject filenames exceeding 255 chars in `validateFileGuard`.

---

## F8 — LOW | platform_post_id / title Silently Truncated or 409 on Overflow

**Location:** `ExcelRowMapper.java:386–387`, `ExcelImportService.java:88`

**Problem:** `platform_post_id` is `VARCHAR(255)` (entity `length=255`) and `title` is `VARCHAR(500)`. The import path reads raw cell values without length validation. A cell value exceeding the column length produces `DataIntegrityViolationException` from `saveAll` → HTTP 409 "Data conflict" with no row-level detail. The batch transaction rolls back; the persisted PROCESSING batch is also lost (same root cause as F4).

**Impact:** Wrong HTTP status, misleading error message, missing audit record.

**Fix:** Add length validation in `validateRows` (or `ExcelRowMapper`) alongside the null/blank checks.

---

## F9 — LOW | IllegalStateException from ReflectionRowWriter Exposes Field Name in Export Response

**Location:** `ReflectionRowWriter.java:74–75`

**Problem:** If `field.get(model)` throws `IllegalAccessException` (theoretically impossible after `setAccessible(true)`, but possible if a `SecurityManager` is installed or module system restricts access), the exception is re-thrown as `IllegalStateException("Không thể truy cập field: " + fields[i].getName())`. This propagates to `GlobalExceptionHandler.handleInternalState` → 500 response with `fields[i].getName()` (an internal model field name) exposed.

**Impact:** Internal DTO field names visible in response. Low severity on its own, combined with F1.

**Fix:** The fix for F1 covers this: never echo `ex.getMessage()` in error responses.

---

## Supply Chain Assessment

- **poi-ooxml 5.3.0**: No known unpatched CVEs as of this review date (July 2025). POI 5.3.0 patches CVE-2024-31948 (Strict OOXML XXE) and includes `ZipSecureFile` protections.
- **commons-compress 1.26.2**: CVE-2024-25710 and CVE-2024-26308 (zip bomb / BZIP2 loop) were fixed in 1.26.0. Version 1.26.2 is clean.
- **log4j-api 2.25.4**: Only the API bridge (log4j-to-slf4j); `log4j-core` is NOT on the classpath. Log4Shell (CVE-2021-44228) is not reachable.

---

## Known-Safe Claims — Refutation Attempts

- **Formula/CSV injection via `setCellValue(String)`**: confirmed safe — string cells produce `ST_CellType.s` in OOXML, no formula evaluation on open. Not refuted.
- **Zip bomb via `ZipSecureFile` defaults**: technically mitigated (default minInflateRatio=0.01), but the absolute size cap is ~4GB per entry. A 10MB upload can still cause significant heap pressure before ZipSecureFile rejects (see F6). Partially refuted — defaults protect against extreme cases, not moderate ones.
- **`@Transactional(readOnly=true)` holds session for lazy access in export**: confirmed correct — the session is open for the duration of `buildExportBytes` including the N+1 metric queries. Not refuted.

---

## Summary Table

| ID | Severity | Category | Location |
|----|----------|----------|----------|
| F1 | Medium | Info Leakage | GlobalExceptionHandler:67 |
| F2 | Medium | Error Handling / Info Leakage | ExcelRowMapper:73 |
| F3 | Medium | Data Correctness | ExcelRowMapper:182 |
| F4 | Medium | Data Integrity / Race Condition | ExcelImportService:69,89 |
| F5 | Low | Data Correctness | ExcelImportService:107,158 |
| F6 | Low | Resource Exhaustion | ExcelImportService:54 |
| F7 | Low | Input Validation | ExcelImportService:65 |
| F8 | Low | Input Validation | ExcelRowMapper / ExcelImportService:88 |
| F9 | Low | Info Leakage | ReflectionRowWriter:74 |

**Status:** DONE_WITH_CONCERNS — no critical findings, but F4 (audit record loss on IOException + concurrent race) and F2 (unhandled FORMULA cell → 500) are medium-severity issues worth fixing before the feature is relied on for audit or production use.

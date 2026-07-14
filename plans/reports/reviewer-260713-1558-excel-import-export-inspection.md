---
reviewer: reviewer
date: 2026-07-13
feature: Excel Import/Export (F002/F003), tasks D2-06..D2-11
score: 8/10 → 9/10 (post-fix re-inspection)
decision: SEALED
---

## Code Review Summary

### Scope
- **New files (16):** ExcelImportService, ImportBatchService, ExcelExportService, ExcelRowMapper, ReflectionRowWriter, ImportBatchResponse, ExportRowModel, ImportController, ExcelExportController, ExcelImportServiceTest, ExcelFixtureBuilder, F002/F003 specs + edge cases, evidence dir, plan dir
- **Modified (5):** pom.xml, application.properties, PostRepository, UserRepository, GlobalExceptionHandler
- **LOC reviewed:** ~650 source, ~300 test
- **Tests:** 23/23 passing (compile + unit suite)

---

### Overall Assessment

Solid, spec-aligned implementation. All critical business rules (validate-first, all-or-nothing, seed-user FK, case-insensitive headers, UTC parsing, non-xlsx 400, unbounded ACTIVE fetch, reflection row writer) are present and functionally correct. The main defect worth calling out is a transaction atomicity gap in the import success path — the `@Transactional` on `persistSuccess()` is dead due to Spring AOP self-invocation, breaking the "single transaction" guarantee from BR-004.

---

### Critical Issues

None that block the feature from functioning at D2 demo scale. The atomicity defect below is high-severity but survivable at low concurrency.

---

### High Priority

#### H-1 — `@Transactional` on `persistSuccess()` is a dead annotation (BR-004 atomicity broken)

`ExcelImportService.importPosts()` calls `this.persistSuccess()` directly — Spring AOP proxy is bypassed. The annotation has no effect.

**Consequence:** `postRepository.saveAll()` and `importBatchService.save(batch)` execute in separate transactions. If `saveAll()` commits posts and then `save(batch)` throws (e.g., `DataIntegrityViolationException` on `importedAt` NOT NULL, or any transient infra error), posts exist in DB with the batch stuck in `PROCESSING` status forever.

**Fix options (pick one):**
1. Move `persistSuccess` to `ImportBatchService` (already a separate bean) and call it from there. `ExcelImportService` delegates via `importBatchService.persistSuccessAtomic(batch, posts)`.
2. Inject `ExcelImportService` via `ApplicationContext` self-injection so the proxy is invoked.
3. Promote `@Transactional` to class level on `ExcelImportService` — simpler, propagates to all public methods including `importPosts()` itself, so the workbook parsing also happens inside a transaction (slight overhead but safe).

Option 3 is the simplest one-liner fix:
```java
@Service
@RequiredArgsConstructor
@Transactional  // ← add here
public class ExcelImportService {
```

---

#### H-2 — `file.getOriginalFilename()` can return `null` → NOT NULL constraint violation

`createProcessingBatch(seedUser, file.getOriginalFilename())` passes the result directly to `ImportBatch.fileName` which is `@Column(nullable = false)`. If a caller omits the filename (programmatic invocation, some CDN proxies strip the filename header), this produces a `DataIntegrityViolationException` → 409 (wrong HTTP semantics for this scenario).

**Fix:** null-safe default before passing:
```java
String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.xlsx";
ImportBatch batch = importBatchService.createProcessingBatch(seedUser, fileName);
```

---

### Medium Priority

#### M-1 — `IOException` message leaked to client

`ExcelImportService` line 82:
```java
throw new IllegalArgumentException("Không đọc được file Excel: " + e.getMessage(), e);
```
The `IOException.getMessage()` from POI (or Spring multipart) can include internal detail: inflate ratios from zip-bomb detection, internal path fragments, stream state. This propagates to the `{"error": "..."}` response body.

**Fix:** strip the IOE message; log it instead:
```java
log.warn("Failed to read Excel file '{}': {}", file.getOriginalFilename(), e.getMessage());
throw new IllegalArgumentException("Không đọc được file Excel — file không hợp lệ hoặc bị hỏng");
```

#### M-2 — `IllegalStateException` handler leaks internal detail

`GlobalExceptionHandler` returns `"Internal server error: " + ex.getMessage()` for `IllegalStateException`. For the seed-user case this exposes `"No seed user found — environment misconfigured (no User row in DB)"` verbatim. Acceptable for dev but worth sanitizing before D3:
```java
.body(Map.of("error", "Internal server error — contact the administrator"))
```
(log `ex.getMessage()` at ERROR level instead — already done.)

#### M-3 — Row number reporting in `validateRows()` is inaccurate when blank/error rows precede valid rows

`validateRows()` iterates `parsedPosts` (already filtered — blank/parse-error rows excluded). `displayRow = i + 2` assumes contiguous data rows. If rows 2-3 were blank or had parse errors, post `i=0` is actually row 4 but is logged as row 2.

Row numbers go only to `log.warn()`, not to the HTTP response (spec says counts-only). Impact: misleading debug logs only.

**Fix (low urgency):** store the original 1-based row index in each `Post` during `mapRows()` (e.g., a transient field or a parallel list), then use it in `validateRows()`.

#### M-4 — `validateFileGuard` accepts non-xlsx extension if content-type is xlsx-like

The guard's OR semantics (`!validExtension && !validContentType` → reject) means a file with extension `.csv` but content-type `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` passes. POI is the real gatekeeper (will throw `OLE2NotOfficeXmlFileException`), so the functional behavior is still correct — the guard is defense-in-depth only. Acceptable as-is.

#### M-5 — `ExcelExportController` sets `Content-Type` / `Content-Disposition` BEFORE calling `buildExportBytes()`

If `buildExportBytes()` throws `IOException` after those two headers are set but before any bytes are written, Spring MVC may or may not be able to override the response status to 500 (depends on whether the Tomcat buffer is flushed). In practice, `ByteArrayOutputStream.write()` almost never throws, so this is low-probability. The safer pattern is to build bytes first, then set headers:
```java
byte[] bytes = excelExportService.buildExportBytes();  // may throw → no headers written yet
response.setContentType(CONTENT_TYPE_XLSX);
response.setHeader(...);
response.setContentLength(bytes.length);
response.getOutputStream().write(bytes);
```
This is already nearly the case — only two lines need swapping (lines 49-50 after line 52).

---

### Low Priority

#### L-1 — `persistFailure()` has no `@Transactional` (intended or oversight?)

`persistFailure()` calls only `importBatchService.save(batch)` which is itself `@Transactional`. No atomicity concern here because no posts are written. Consistent behavior is OK; minor: add a comment to make the omission explicit.

#### L-2 — `ReflectionRowWriter` depends on `getDeclaredFields()` declaration order

Spec explicitly acknowledges this as a HotSpot-JVM assumption. Comment in code is present. No action needed at D2; `@ExcelColumn` order annotation is planned for D6. Acknowledged in edge-cases.md.

#### L-3 — No CSV/formula injection risk in export (confirmed safe)

`ReflectionRowWriter.writeRow()` uses `setCellValue(String)` which sets `CellType.STRING`, not `CellType.FORMULA`. Excel does not evaluate formula syntax in STRING-type cells. A `title` containing `=HYPERLINK(...)` stored in DB renders as a literal string cell. No action needed.

#### L-4 — POI zip-bomb defaults confirmed active

No `ZipSecureFile.setMinInflateRatio(0)` or similar override found. POI 5.3.0 ships with zip-bomb protection ON by default. No action needed.

#### L-5 — `content` field imported but not exported (intentional per spec)

`ExcelRowMapper` reads `content` from the sheet; `ExportRowModel` does not include it. Spec ALG-001 column list for export does not list `content`. Intentional; no action needed.

---

### Edge Cases Found (Scouting)

1. **`file.getOriginalFilename() == null` → NOT NULL DB violation** (covered in H-2 above)
2. **Empty `SocialProvider` enum with invalid value silently returns `null`** — `parsePlatform()` catches `IllegalArgumentException` and returns `null`. The service then correctly flags the row via BR-001/BR-002. Behavior is correct but the silent swallow is worth noting — a FORMULA cell type for the platform column would also hit the `default → null` branch in `getCellString()`, resulting in null platform → error message says "platform is required" rather than "platform is an invalid type". Acceptable.
3. **`sheet.getLastRowNum()` returns 0 for a file with exactly one row** — that one row is the header; `<= 0` check correctly rejects it. Correct.
4. **Duplicate in-file detection uses `fileKey = platform.name() + "|" + platformPostId`** — pipe `|` is a valid character in `platformPostId` strings, creating a theoretical collision between `(FACEBOOK|, postId)` and `(FACEBOOK, |postId)`. Extremely contrived; not a real risk at D2.

---

### Positive Observations

- Validate-first pattern is clean and disciplined: no optimistic commits.
- `@Transactional(readOnly = true)` on `buildExportBytes()` correctly holds the Hibernate session for the N+1 metric loop — prevents `LazyInitializationException` with `open-in-view=false`.
- `ExcelFixtureBuilder` produces real POI workbooks — tests exercise actual parse paths, not faked byte arrays.
- `GlobalExceptionHandler` correctly maps `MaxUploadSizeExceededException` → 400 (not the default 413).
- `ZipSecureFile` left at POI default — no override, protection is on.
- DTOs are Java `record`s with `static from(entity)` factories — matches project convention.
- All `ImportBatch` state transitions follow SM-001 (PENDING → PROCESSING → DONE/FAILED).
- Vietnamese comments throughout are consistent with codebase convention.

---

### Recommended Actions (prioritized)

1. **[H-1] Fix `@Transactional` self-invocation** — add `@Transactional` at the class level on `ExcelImportService`, or move `persistSuccess()` to a separate bean. This is the only BR-004 atomicity violation.
2. **[H-2] Guard `file.getOriginalFilename()` null** — one-liner null check with fallback.
3. **[M-1] Strip `IOException` message from client response** — log it internally, return generic message.
4. **[M-5] Swap header-setting and `buildExportBytes()` order** — two-line reorder, best practice.
5. **[M-3] Row number tracking** — low urgency; log-only impact.

---

### Metrics

| Metric | Value |
|--------|-------|
| Test coverage (new code) | ~70% (8 unit scenarios; no slice/integration tests at D2) |
| Linting issues | 0 (compiles clean) |
| Critical defects | 0 |
| High defects | 2 (H-1 atomicity, H-2 null filename) |
| Medium defects | 5 |
| Low / suggestions | 5 |

---

### Unresolved Questions

1. Is `ImportBatch.totalRecords` intended to exclude completely blank rows (current behavior), or count them? Spec is ambiguous on this — the edge-cases.md doesn't address blank rows inside an otherwise valid file.
2. Should `content` field be added to the export report in a future iteration, or is it intentionally excluded long-term?

---

## Re-inspection — Post-fix Verification (2026-07-13)

**New score: 9/10**

All four issues verified by direct file read (files are untracked; `git diff HEAD` was empty but the actual on-disk content was compared line-by-line).

### H-1 fix — confirmed correct

`@Transactional` moved to `importPosts()` (line 49, proxy entry point). `persistSuccess()` and `persistFailure()` are now `private` — self-invocation is moot; they participate in the transaction opened by `importPosts()`. All Spring Data calls (`saveAll`, `importBatchService.save`, `createProcessingBatch`) use default `REQUIRED` propagation and join the outer transaction. The "single transaction" guarantee from BR-004 is now real. A bonus improvement: if `IOException` is thrown during workbook parsing (before `createProcessingBatch`), the transaction rolls back — no orphan PROCESSING batch. Consistent with spec US003. The comment on line 45-48 correctly documents the reasoning.

### H-2 fix — confirmed correct

Lines 64-66: `file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.xlsx"` before `createProcessingBatch`. NOT NULL constraint can no longer be violated by a null filename. The `validateFileGuard` (line 195) still uses the raw `file.getOriginalFilename()` for extension checking — intentional and correct (null there only means extension check is skipped, content-type check is the fallback).

### M-1 fix — confirmed correct

Lines 90-92: `e.getMessage()` is logged at WARN level internally; client message is the static string `"Không đọc được file Excel (file hỏng hoặc sai định dạng)"` — no POI internals exposed.

### M-5 fix — confirmed correct

`buildExportBytes()` called at line 51, before `setContentType` (line 53) and `setHeader` (line 54). If the service throws, Spring can still set status 500 on the uncommitted response.

### Residual items (unchanged)

M-2, M-3, M-4 remain as documented — all low-urgency, none block D2 delivery. No new issues introduced by the fixes.

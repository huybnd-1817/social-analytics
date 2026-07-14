---
status: draft
authored_by: takumi
created: 2026-07-13
lang: en
---

# F002_ExcelExportReport — Technical Spec

**Priority**: P0
**Type**: background
**Generated**: 2026-07-13

## Overview

ExcelExportReport exposes a single `GET /export-report` endpoint that streams a timestamped `.xlsx` file containing all ACTIVE posts paired with their latest social-metric snapshot. The service fetches active posts via the existing `PostService` / `PostRepository.findByStatus(ACTIVE)` convention, resolves the latest `SocialMetric` per post through `SocialMetricRepository.findTop1ByPostOrderByCrawledAtDesc` (N+1 accepted at D2 demo scale, YAGNI), and delegates cell writing to a generic `ExcelExportService` reflection-based row writer that derives column headers from the declared fields of a flat row-model class. No database write occurs; the endpoint is idempotent. No authentication is enforced at D2 scope.

## Polymorphic Behavior

N/A — no discriminator fields in Key Entities.

> Post.status and Post.platform are enum fields but neither drives per-value behavioral branching within this feature: only ACTIVE posts are selected (a filter, not a branch), and platform is written as a plain string cell. No DISC-### table applies.

## Cross-Cutting Logic

### Requirements

| Code | Description | Endpoint/Handler | Verifiable |
|------|-------------|------------------|------------|
| FR-001 | Stream `.xlsx` response with `Content-Disposition: attachment; filename=report_{timestamp}.xlsx` and `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | `GET /export-report` | yes |
| FR-002 | Export only posts with `status = ACTIVE` | `GET /export-report` | yes |
| FR-003 | Each row carries the post fields plus the latest metric counters; posts with no metric rows must still appear with empty/zero metric cells | `GET /export-report` | yes |
| FR-004 | Column headers and cell values are derived dynamically from the row-model class field declarations (reflection-based writer); no hardcoded column list in the writer | `ExcelExportService` | yes |
| FR-005 | Workbook written using a streaming-friendly POI API (SXSSF or XSSF via `poi-ooxml 5.3.0`); exact class choice left to implementation | `ExcelExportService` | yes |

### Business Rules

#### BR-001_ActivePostsOnly
**Linked FR:** FR-002
**Applies to:** `GET /export-report` — post selection
**Rule:** Only posts whose `status = PostStatus.ACTIVE` are included in the export. Soft-deleted posts (`DELETED`) are silently excluded.

**Pseudocode:**
```text
posts = postRepository.findByStatus(PostStatus.ACTIVE)
// deleted posts never reach the workbook
```

#### BR-002_EmptyMetricFallback
**Linked FR:** FR-003
**Applies to:** metric resolution per post
**Rule:** If `findTop1ByPostOrderByCrawledAtDesc(post)` returns `Optional.empty()`, all metric counter cells for that row are written as `0` (or left blank — implementer choice); the row is never omitted.

**Pseudocode:**
```text
for post in activePosts:
    metric = metricRepo.findTop1ByPostOrderByCrawledAtDesc(post)
               .orElse(null)  // or a zero-value sentinel
    row = buildRowModel(post, metric)  // null metric → zero counters
    writer.writeRow(sheet, row)
```

#### BR-003_TimestampedFilename
**Linked FR:** FR-001
**Applies to:** HTTP response header
**Rule:** The `Content-Disposition` filename includes a timestamp suffix to make repeated downloads distinguishable without server-side state. Format: `report_yyyyMMddHHmmss.xlsx` (or equivalent ISO-safe format).

**Pseudocode:**
```text
timestamp = LocalDateTime.now().format("yyyyMMddHHmmss")
filename  = "report_" + timestamp + ".xlsx"
headers.setContentDisposition(
    ContentDisposition.attachment().filename(filename).build()
)
```

### Decision Logic

N/A — no user-facing decision logic beyond DISC-### Polymorphic Behavior.

> The only branching is metric-null fallback (BR-002) and post-filter (BR-001); neither produces a user-visible outcome beyond cell values in the downloaded file. Both are classified as Business Rules, not DEC-###.

### State Machines

None. The endpoint is stateless and idempotent; no entity lifecycle transitions occur.

### Algorithms

#### ALG-001_ReflectionRowWriter
**Linked FR:** FR-004
**Input:** A flat row-model POJO class + one populated instance per post
**Output:** A single XSSF/SXSSF row appended to the active sheet
**File Schema:**

| Column | Type | Required | Notes |
|--------|------|----------|-------|
| platform | String | yes | `SocialProvider` enum name |
| platformPostId | String | yes | — |
| title | String | no | nullable → empty cell |
| postUrl | String | no | nullable → empty cell |
| publishedAt | String | no | ISO-8601 formatted or empty |
| status | String | yes | `PostStatus` enum name |
| likesCount | Long | no | 0 if no metric |
| sharesCount | Long | no | 0 if no metric |
| commentsCount | Long | no | 0 if no metric |
| followersCount | Long | no | 0 if no metric |
| reach | Long | no | 0 if no metric |
| impressions | Long | no | 0 if no metric |
| crawledAt | String | no | ISO-8601 or empty |

**Complexity:** O(F) per row, where F = number of declared fields (constant at runtime)
**Description:** The writer introspects the row-model class via `Class.getDeclaredFields()` to build the header row on the first invocation, then maps each field value to a cell using `field.get(instance)`. Null values produce an empty string cell. Type coercion: `Number` → numeric cell; everything else → string cell. This pattern generalizes across any flat POJO without per-feature column registration. A future `@ExcelColumn` annotation (D6 evolution) will replace raw field ordering.

**Pseudocode:**
```text
fields = rowModelClass.getDeclaredFields()
// header (once)
headerRow = sheet.createRow(0)
for i, field in enumerate(fields):
    headerRow.createCell(i).setCellValue(field.getName())

// data rows
for rowIdx, instance in enumerate(dataRows, start=1):
    row = sheet.createRow(rowIdx)
    for i, field in enumerate(fields):
        field.setAccessible(true)
        value = field.get(instance)
        cell  = row.createCell(i)
        if value instanceof Number:
            cell.setCellValue(((Number) value).doubleValue())
        else:
            cell.setCellValue(value != null ? value.toString() : "")
```

### External Integrations

None. All data is read from the local PostgreSQL database; no third-party service is called.

### Verification

- **SC-001** — `GET /export-report` returns HTTP 200, `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, and `Content-Disposition: attachment; filename=report_*.xlsx` (covers FR-001)
- **SC-002** — The downloaded file is a valid `.xlsx`; its header row matches the field names of the row-model class in declaration order (covers FR-004)
- **SC-003** — All rows in the sheet have `status = ACTIVE`; no DELETED post appears (covers FR-002, BR-001)
- **SC-004** — A post with no `SocialMetric` row appears in the sheet with `0` / empty metric cells, not omitted (covers FR-003, BR-002)
- **SC-005** — The filename in `Content-Disposition` contains a timestamp substring matching `\d{14}` or equivalent (covers BR-003)

---

**Client behavior:** see
[`behavior-logic.md`](../../docs/system/behavior-logic.md) (client-side patterns — debounce, optimistic UI, polling, upload, realtime),
[`permissions.md`](../../docs/system/permissions.md) (feature flags / experiments / env / locale gates),
[`architecture.md`](../../docs/system/architecture.md) (guards / deep-link state restoration / unsaved-changes protection).

> N/A for all three linked artifacts — this is a pure server-side streaming endpoint with no client-side logic.

## User Stories

### US001_StreamExcelReport — Download Active Posts & Metrics as Excel (Priority: P0)

**What happens:** A caller issues `GET /export-report`. The controller delegates to `ExcelExportService`, which queries all ACTIVE posts, resolves the latest metric per post, builds a workbook via the reflection-based row writer, and writes the byte stream to the HTTP response output stream. The caller receives a `.xlsx` file download.

**Why this priority:** P0 — core deliverable for D2-10. Without this endpoint the Excel export sprint cannot be validated. No downstream feature depends on it, but it is the sole user-visible output of this feature.

**Independent Test:** Issue `GET http://localhost:8080/export-report`; verify HTTP 200, correct MIME type header, and that the response body opens as a valid Excel workbook with at least one data row per ACTIVE post in the database.

**Acceptance Scenarios:**

1. **Given** the database contains 3 ACTIVE posts (2 with metric rows, 1 without), **When** `GET /export-report` is called, **Then** the response is HTTP 200 with a `.xlsx` attachment containing a header row and 3 data rows; the post with no metric has `0` in all counter cells.
2. **Given** the database contains 0 ACTIVE posts, **When** `GET /export-report` is called, **Then** the response is HTTP 200 with a `.xlsx` attachment containing only the header row (no data rows).
3. **Given** `SocialMetricRepository.findTop1ByPostOrderByCrawledAtDesc` returns multiple snapshots for a post, **When** `GET /export-report` is called, **Then** only the most recent snapshot's counters appear in the row (query selects Top 1 by `crawledAt DESC`).

**Requirements fulfilled:**
- **FR-001** Stream `.xlsx` response with correct MIME type and `Content-Disposition` attachment header — `GET /export-report` via `ExcelExportController::exportReport`
- **FR-002** Export only ACTIVE posts — `GET /export-report` via `PostRepository::findByStatus(ACTIVE)`
- **FR-003** Posts with no metric still exported with zero/empty metric cells — `ExcelExportService::buildRows`
- **FR-004** Column headers derived from row-model field declarations — `ExcelExportService::writeHeader`
- **FR-005** Workbook written using streaming-friendly POI API — `ExcelExportService` (SXSSF/XSSF)

**Rules enforced:**

#### BR-001_ActivePostsOnly — see Cross-Cutting Logic

#### BR-002_EmptyMetricFallback — see Cross-Cutting Logic

#### BR-003_TimestampedFilename — see Cross-Cutting Logic

**Verification:**
- **SC-001** through **SC-005** (see Cross-Cutting Logic § Verification)

---

### Edge Cases

See `edge-cases.md`.

## Key Entities

| Entity | Table | Key Columns | Purpose |
|--------|-------|-------------|---------|
| Post | `posts` | `id`, `platform`, `platform_post_id`, `title`, `post_url`, `published_at`, `status` | Source of all exported post fields; filtered to `status = ACTIVE` |
| SocialMetric | `social_metrics` | `id`, `post_id`, `likes_count`, `shares_count`, `comments_count`, `followers_count`, `reach`, `impressions`, `crawled_at` | Latest metric snapshot per post; resolved via `findTop1ByPostOrderByCrawledAtDesc`; composite index `(post_id, crawled_at DESC)` makes this efficient |
| User | `users` | `id`, `email`, `name`, `role` | Owned by Post via `user_id FK` (NOT NULL); not exported (no author column per resolved decision); required to be present for existing post rows |

## Artifact References

| Artifact | File | Codes Used | Reviewed |
|----------|------|------------|----------|
| System Overview | `docs/system/system-overview.md` | — | [ ] |
| Architecture | `docs/system/architecture.md` | — | [ ] |
| Feature List | `plans/260713-1152-excel-import-export/spec/feature-list.md` | F002_ExcelExportReport (provisional) | [x] |
| API Map | TBD (draft) | TBD (draft) | [ ] |
| Entities | TBD (draft) | TBD (draft) | [ ] |
| Screens | `plans/260713-1152-excel-import-export/spec/excel-export-report/screens.md` | TBD (draft) | [ ] |
| Behavior Logic | TBD (draft) | TBD (draft) | [ ] |
| Permissions Matrix | TBD (draft) | TBD (draft) | [ ] |
| User Stories | TBD (draft) | US001 | [ ] |

## Assumptions

- **Auth deferred**: No authentication or authorization is enforced at D2. The endpoint is open. Auth (OAuth2) lands in D3; PERM### codes will be allocated then.
- **N+1 accepted**: One `SELECT` per post to fetch the latest metric is acceptable at D2 demo scale. The composite index `(post_id, crawled_at DESC)` on `social_metrics` keeps each lookup fast. A bulk `@Query` (DISTINCT ON or ROW_NUMBER window) is deferred to D6 per YAGNI.
- **Row-model field order is deterministic**: `Class.getDeclaredFields()` returns fields in declaration order on the HotSpot JVM (Java 26). The spec relies on this for column ordering; if portability becomes a concern, an explicit `@ExcelColumn(order=N)` annotation is planned for D6.
- **`poi-ooxml 5.3.0` will be present**: The study confirms Apache POI is not yet in `pom.xml`. The D2-06 task adds it. This spec assumes that dependency is resolved before implementation begins.
- **No user/author column**: Confirmed by resolved design decision. `Post.user` is not fetched eagerly for export; the FK exists but is not written to any cell.
- **Single sheet**: The workbook contains exactly one sheet named (e.g.) `"Report"`. Multi-sheet layout is out of scope.

## Source Code References

No source code written yet — see `## User Stories` for planned endpoints and service structure.

Planned symbols (to be cited after implementation):

| Symbol (planned) | Path (planned) | Purpose |
|------------------|----------------|---------|
| `ExcelExportController` | `src/main/java/.../controller/ExcelExportController.java` | `GET /export-report` handler; sets response headers and streams workbook |
| `ExcelExportService` | `src/main/java/.../service/ExcelExportService.java` | Orchestrates post fetch → metric resolution → workbook build |
| `PostRepository` | `src/main/java/.../repository/PostRepository.java` | `findByStatus(PostStatus, Pageable)` — active post selection |
| `SocialMetricRepository` | `src/main/java/.../repository/SocialMetricRepository.java` | `findTop1ByPostOrderByCrawledAtDesc(Post)` — latest metric lookup |
| `ExportRowModel` | `src/main/java/.../dto/ExportRowModel.java` | Flat POJO whose fields define the workbook schema via reflection |

## Unresolved Questions

All gaps resolved with the user on 2026-07-13:

1. **Sheet name** → RESOLVED: implementer choice (sensible default such as `"Report"`).
2. **Date/time format for cells** → RESOLVED: human-readable string `yyyy-MM-dd HH:mm:ss` (UTC) for `publishedAt` / `crawledAt`; no native Excel date cells at D2.
3. **Paginated vs full fetch** → RESOLVED: unbounded fetch of all ACTIVE posts (single list query, no pagination) — consistent with accepted N+1 at demo scale.

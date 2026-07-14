---
title: "Excel Import/Export for Posts & Metrics"
description: "Bulk-import posts from .xlsx (all-or-nothing) and stream a posts+metrics report as .xlsx"
status: completed
priority: P1
effort: 9h
progress: 100%
branch: feature/export-import-excel
work_type: feature
spec:
  - docs/features/F002_ExcelBulkImportPosts/
  - docs/features/F003_ExcelExportReport/
tags: [excel, poi, import, export, backend]
created: 2026-07-13
---

# Excel Import/Export for Posts & Metrics

Backend-only feature (no UI). Two capabilities: bulk `.xlsx` post import with validate-first
all-or-nothing semantics (F002), and a streamed `.xlsx` posts+metrics report (F003).
Stack: Java 26, Spring Boot 4.1.0, Apache POI `poi-ooxml` 5.3.0, package
`com.sunasterisk.socialanalytics`. All key design decisions are pre-resolved in the spec drafts.

## Feature Codes (provisional)

- **F001_ExcelBulkImportPosts** — `POST /import-posts`
- **F002_ExcelExportReport** — `GET /export-report`

## Phases

| # | Phase | Feature | Tasks | Status |
|---|-------|---------|-------|--------|
| 01 | POI dependency + multipart config | F002, F003 | D2-06 | completed |
| 02 | Excel import service + endpoint + exception handling | F002 | D2-07, D2-08 | completed |
| 03 | Excel export service + endpoint | F003 | D2-09, D2-10 | completed |
| 04 | ExcelImportService unit tests | F002 | D2-11 | completed |

## Dependency Graph

- Phase 01 → foundation; blocks 02 and 03 (both need POI on classpath).
- Phase 02 and Phase 03 are **independent** after 01 — no shared file → parallel-runnable.
- Phase 04 depends on Phase 02 (tests the code it produces).

```
01 ──┬── 02 ── 04
     └── 03
```

## File Ownership (no overlap between parallel phases)

- Phase 01: `pom.xml`, `src/main/resources/application.properties`
- Phase 02: `controller/ImportController`, `service/ExcelImportService`,
  `service/ImportBatchService`, `util/ExcelRowMapper`, `dto/ImportBatchResponse`,
  `repository/PostRepository` (+method), `controller/GlobalExceptionHandler` (+handlers)
- Phase 03: `controller/ExcelExportController`, `service/ExcelExportService`,
  `util/ReflectionRowWriter`, `dto/ExportRowModel`, `repository/PostRepository` (+method)
- Phase 04: `src/test/.../service/ExcelImportServiceTest`

> Note: both 02 and 03 add a method to `PostRepository`. To keep them parallel-safe,
> Phase 01 adds **both** repository methods (`findByStatus(status)` list overload +
> `findActiveByPlatformAndPlatformPostId` bulk lookup) so neither later phase touches it.

## Success Criteria (observable)

- `./mvnw -q compile` passes after every phase.
- SC-001..SC-004 (import) and SC-001..SC-005 (export) from the specs verifiable via curl / test.
- `ExcelImportServiceTest` green: valid file, missing columns, empty file.

## Links

- Study: `plans/reports/researcher-260713-1149-excel-import-export-study.md`
- Spec: `plans/260713-1152-excel-import-export/spec/`

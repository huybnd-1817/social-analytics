# Feature List

## Feature Hierarchy

| # | Feature | Priority | Type | Status |
|---|---------|----------|------|--------|
| 1 | F001 | Post Metric Test Suite | P1 | background | implemented |
| 2 | F002 | Excel Bulk Import Posts | P0 | background | implemented |
| 3 | F003 | Excel Export Report | P0 | background | implemented |

## Feature Details

### F001 — Post Metric Test Suite

**Priority:** P1 | **Type:** background | **Status:** implemented | **Slug:** F001_PostMetricTestSuite

Test infrastructure for Post/Metric services, repository, and controller (D2-01..D2-05): H2 test dependency, Mockito unit tests, @DataJpaTest slice, @WebMvcTest MockMvc tests.

**Related:** screens: — | routes: — | models: —

### F002 — Excel Bulk Import Posts

**Priority:** P0 | **Type:** background | **Status:** implemented | **Slug:** F002_ExcelBulkImportPosts

Bulk-import posts from .xlsx via POST /import-posts — validate-first, all-or-nothing, ImportBatch summary.

**Related:** screens: — | routes: — | models: —

### F003 — Excel Export Report

**Priority:** P0 | **Type:** background | **Status:** implemented | **Slug:** F003_ExcelExportReport

Stream .xlsx report of ACTIVE posts + latest social metrics via GET /export-report.

**Related:** screens: — | routes: — | models: —

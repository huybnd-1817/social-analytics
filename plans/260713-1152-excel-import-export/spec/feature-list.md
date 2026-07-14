---
status: draft
authored_by: takumi
created: 2026-07-13
---

# Feature List

<!-- NOTE: docs/_canonical-fcodes.json already reserves F002 (F001_PostMetricTestSuite).
     Codes below are PROVISIONAL F002..F003 — real allocation (starting from the next
     available code) happens at promote via the id_contiguity machinery. -->

**Project**: social-analytics
**Generated**: 2026-07-13
**Analysis Scope**: D2-06..D2-11 — Excel bulk-import posts + Excel export posts+metrics report

**Code Format**: `F###_NameSlug` (no hyphen; e.g. F001_Auth)
**Background Logic Code Format**: `BL###_NameSlug`
**Permission Code Format**: `PERM###_NameSlug`

**Feature Types**:
- `background` — feature has only background/API logic (no SCR###)

---

## Feature Hierarchy

| Code | Name | Type | Language | Workspace | Priority |
|------|------|------|----------|-----------|----------|
| F001_ExcelBulkImportPosts | Excel Bulk Import Posts | background | en | social-analytics (Spring Boot) | P0 |
| F002_ExcelExportReport | Excel Export Posts & Metrics Report | background | en | social-analytics (Spring Boot) | P0 |

---

## Feature Details

### F001_ExcelBulkImportPosts: Excel Bulk Import Posts

**Type**: background
**Description**: User uploads an `.xlsx` file via `POST /import-posts`; the system validates every row first (validate-first / all-or-nothing), then either persists all posts under a single `ImportBatch` with `status=DONE` or rejects the entire batch with `status=FAILED` and persists nothing. Returns an `ImportBatch` summary (totalRecords / successRecords / failedRecords).

**Workspace**: social-analytics (Spring Boot, Java 26)
**Languages**: en
**Components**: Controller (`POST /import-posts`), ExcelImportService, ImportBatchService (new), `ImportBatch` entity, `Post` entity, seed-user fallback (pre-OAuth2)

**Related Screens**:
- TBD (draft) — backend-only feature; no UI screen at D2 scope

**Related User Stories**:
- TBD (draft)

**Related APIs/Routes**:
- (POST) /import-posts — multipart/form-data; field `file` (.xlsx); returns `ImportBatchResponse`

**Related Data Models**:
- ImportBatch
- Post

**Related Background Logic**:
- TBD (draft)

**Related Permissions**:
- TBD (draft)

---

### F002_ExcelExportReport: Excel Export Posts & Metrics Report

**Type**: background
**Description**: User calls `GET /export-report`; the system fetches all active posts, resolves the latest `SocialMetric` per post via the existing `findTop1ByPostOrderByCrawledAtDesc` query (N+1 accepted at demo scale), and streams a `.xlsx` file as a download using a reflection-based row writer. No DB write; idempotent.

**Workspace**: social-analytics (Spring Boot, Java 26)
**Languages**: en
**Components**: Controller (`GET /export-report`), ExcelExportService, reflection-based row writer, `SocialMetric`/`Post` read path

**Related Screens**:
- TBD (draft) — backend-only feature; no UI screen at D2 scope

**Related User Stories**:
- TBD (draft)

**Related APIs/Routes**:
- (GET) /export-report — no body; response Content-Type `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, Content-Disposition attachment

**Related Data Models**:
- Post
- SocialMetric

**Related Background Logic**:
- TBD (draft)

**Related Permissions**:
- TBD (draft)

---

## Summary

- **Total Features**: 2
- **Total Screens**: 0 (backend-only)
- **Total User Stories**: TBD (draft)
- **Total Routes**: 2
- **Total Data Models**: 3 (Post, ImportBatch, SocialMetric)
- **Total Background Logic**: TBD (draft)
- **Total Permissions**: TBD (draft)
- **Languages Detected**: en

---

## Cross-Reference Validation

- [x] All F### codes are unique

<!-- Greenfield subset — cross-artifact checks (US###/SCR###/ROUTE###/MODEL###) skipped
     per spec-authoring-contract.md § Greenfield Feature-List: those artifacts not yet generated. -->

---

## Gaps for Clarification

1. question: Which User should own ImportBatch.user and Post.user FK before OAuth2 lands (D3)?
   options: [Seed user id=1 (hardcoded), Request param userId, System/bot user created by migration, TBD]
   recommended: Seed user id=1 (hardcoded)
   category: data-model

2. question: Should the export sheet include a user/author name column (requires eager fetch of Post.user)?
   options: [Yes — add author name column, No — omit user column, TBD]
   recommended: No — omit user column
   category: scope

3. question: Accept N+1 for export at D2 demo scale, or add a bulk latest-metric query now?
   options: [Accept N+1 (YAGNI for demo), Add bulk @Query now, TBD]
   recommended: Accept N+1 (YAGNI for demo)
   category: data-model

4. question: Where to configure multipart size limits (max-file-size=10MB, max-request-size=11MB)?
   options: [Base application.properties (all envs), Dev-only profile only, TBD]
   recommended: Base application.properties (all envs)
   category: other

# Feature List

## Feature Hierarchy

| # | Feature | Priority | Type | Status |
|---|---------|----------|------|--------|
| 1 | F001 | Post Metric Test Suite | P1 | background | implemented |
| 2 | F002 | Excel Bulk Import Posts | P0 | background | implemented |
| 3 | F003 | Excel Export Report | P0 | background | implemented |
| 4 | F004 | OAuth2 Social Login | P0 | mixed | implemented |
| 5 | F005 | Dashboard | P1 | ui | implemented |
| 6 | F006 | Automated Crawl Job | P1 | background | implemented |
| 7 | F007 | JMS Import Pipeline | P1 | background | implemented |

## Feature Details

### F001 — Post Metric Test Suite

**Priority:** P1 | **Type:** background | **Status:** implemented | **Slug:** F001_PostMetricTestSuite

REST endpoints for querying posts and social metrics: paginated GET /posts, soft-delete DELETE /posts/{id}, GET /metrics (aggregated), GET /metrics/last-updated.

**Related:** screens: — | routes: ROUTE008, ROUTE009, ROUTE012, ROUTE013 | models: Post, SocialMetric

---

### F002 — Excel Bulk Import Posts

**Priority:** P0 | **Type:** background | **Status:** implemented | **Slug:** F002_ExcelBulkImportPosts

Bulk-import posts from .xlsx via POST /import-posts — validate-first, all-or-nothing, ImportBatch summary. On success publishes ImportSucceededEvent to trigger async stats recalculation.

**Related:** screens: — | routes: ROUTE010 | models: Post, ImportBatch

---

### F003 — Excel Export Report

**Priority:** P0 | **Type:** background | **Status:** implemented | **Slug:** F003_ExcelExportReport

Stream .xlsx report of ACTIVE posts + latest social metrics via GET /export-report.

**Related:** screens: — | routes: ROUTE011 | models: Post, SocialMetric

---

### F004 — OAuth2 Social Login

**Priority:** P0 | **Type:** mixed | **Status:** implemented | **Slug:** F004_Oauth2SocialLogin

OAuth2 social login via Facebook and Twitter/X; upserts User (role=USER) and SocialAccount (access token); secures all app endpoints via Spring Security filter chain; ships login page (SCR001_Login).

**Related:** screens: SCR001_Login | routes: ROUTE002, ROUTE003, ROUTE004, ROUTE005, ROUTE006, ROUTE007 | models: User, SocialAccount

---

### F005 — Dashboard

**Priority:** P1 | **Type:** ui | **Status:** implemented | **Slug:** F005_Dashboard

Post-login dashboard at GET / — shows authenticated user's name, email (N/A for Twitter), and provider badge; provides logout via POST /logout.

**Related:** screens: SCR002_Dashboard | routes: ROUTE001 | models: —

---

### F006 — Automated Crawl Job

**Priority:** P1 | **Type:** background | **Status:** implemented | **Slug:** F006_AutomatedCrawlJob

Scheduled background job that crawls social metrics for all linked SocialAccounts every hour. Each account is crawled asynchronously (@Async) via SocialCrawlerService to avoid blocking the scheduler thread. Writes a new SocialMetric snapshot row per post per run.

**Related:** screens: — | routes: — | models: SocialMetric, SocialAccount

---

### F007 — JMS Import Pipeline

**Priority:** P1 | **Type:** background | **Status:** implemented | **Slug:** F007_JmsImportPipeline

Async post-import stats recalculation pipeline using embedded ActiveMQ Artemis. After a successful import batch DB commit, ExcelImportService publishes an ImportSucceededEvent; ImportEventProducer converts it to a JMS message on the IMPORT_COMPLETED queue (fires only AFTER_COMMIT — no phantom messages on rollback); ImportEventListener recalculates per-platform ACTIVE post counts and updates ImportStatsCache. Failed processing NAKs → Artemis retries → DLQ.IMPORT_COMPLETED after max-delivery-attempts.

**Related:** screens: — | routes: — | models: ImportCompletedMessage, ImportStats (in-memory), Post

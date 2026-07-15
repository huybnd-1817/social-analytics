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
| 4 | F004 | OAuth2 Social Login | P0 | mixed | implemented |

### F004 — OAuth2 Social Login

**Priority:** P0 | **Type:** mixed | **Status:** implemented | **Slug:** F004_Oauth2SocialLogin

OAuth2 social login via Facebook and Twitter/X; upserts User (role=USER) and SocialAccount (access token); secures all app endpoints via Spring Security filter chain; ships login page (SCR001_Login).

**Related:** screens: SCR001_Login | routes: /login, /oauth2/authorization/facebook, /oauth2/authorization/twitter | models: User, SocialAccount
| 5 | F005 | Dashboard | P1 | ui | implemented |

### F005 — Dashboard

**Priority:** P1 | **Type:** ui | **Status:** implemented | **Slug:** F005_Dashboard

Post-login dashboard at GET / — shows authenticated user's name, email (N/A for Twitter), and provider badge; provides logout via POST /logout.

**Related:** screens: SCR002_Dashboard | routes: / | models: —

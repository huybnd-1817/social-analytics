# User Stories

## Authentication & Session

### US001 — Social Login via Facebook
**As a** social media analyst,  
**I want to** log in using my Facebook account,  
**So that** I can access the application without managing a separate password.

**Acceptance criteria:**
- Clicking the Facebook button redirects to Facebook OAuth2 authorization
- After granting access, I am redirected back and logged in
- My User record is created (or updated) with name, email, and avatar
- My SocialAccount record is created with the Facebook access token

**Feature:** F004 | **Routes:** ROUTE003, ROUTE005

---

### US002 — Social Login via Twitter/X
**As a** social media analyst,  
**I want to** log in using my Twitter/X account,  
**So that** I can use the application even without a Facebook account.

**Acceptance criteria:**
- Clicking the Twitter button redirects to Twitter OAuth2 authorization
- After granting access, I am redirected back and logged in
- Email field may be absent (Twitter does not share email); name is populated from username

**Feature:** F004 | **Routes:** ROUTE004, ROUTE006

---

### US003 — Logout
**As an** authenticated user,  
**I want to** log out,  
**So that** my session is terminated and others cannot use the application on my device.

**Acceptance criteria:**
- Clicking Logout posts to /logout
- Session is invalidated; I am redirected to /login?logout
- A logout confirmation message is shown on the login page

**Feature:** F004 | **Routes:** ROUTE007

---

## Post Management

### US004 — View Post List
**As an** authenticated user,  
**I want to** view a paginated list of ACTIVE posts,  
**So that** I can review imported social media content.

**Acceptance criteria:**
- GET /posts returns a paginated JSON response
- Only ACTIVE posts are shown (soft-deleted excluded)
- Default page size is 20; max is 100

**Feature:** F001 | **Routes:** ROUTE008

---

### US005 — Soft-Delete a Post
**As an** authenticated user,  
**I want to** remove a post from the active list,  
**So that** I can clean up incorrect or unwanted posts without losing the historical record.

**Acceptance criteria:**
- DELETE /posts/{id} sets post status to DELETED
- Post no longer appears in GET /posts results
- The same platform_post_id can be re-imported after deletion

**Feature:** F001 | **Routes:** ROUTE009

---

## Import

### US006 — Bulk Import Posts from Excel
**As an** authenticated user,  
**I want to** upload an Excel file containing social media post data,  
**So that** I can populate the system with historical post records efficiently.

**Acceptance criteria:**
- POST /import-posts accepts a .xlsx file (max 10 MB)
- All rows are validated before any are persisted (validate-first)
- If any row is invalid, the entire batch is rejected with error details
- On success, an ImportBatch record is created with total/success/failed counts
- Duplicate posts (same platform + platform_post_id and ACTIVE) are rejected
- A JSON summary (batchId, counts, status) is returned

**Feature:** F002 | **Routes:** ROUTE010

---

### US007 — View Import Batch Result
**As an** authenticated user,  
**I want to** see a summary of my import batch,  
**So that** I know how many records were imported and whether any failed.

**Acceptance criteria:**
- The POST /import-posts response includes batchId, totalRecords, successRecords, failedRecords, status
- Status is DONE on full success or FAILED on rejected batch

**Feature:** F002 | **Routes:** ROUTE010

---

## Export

### US008 — Export Post Report as Excel
**As an** authenticated user,  
**I want to** download an Excel report of all ACTIVE posts with their latest metrics,  
**So that** I can share the data with stakeholders or perform offline analysis.

**Acceptance criteria:**
- GET /export-report returns a .xlsx file download
- Each row contains post fields (platform, title, content, published_at, etc.) + latest social metric snapshot
- Only ACTIVE posts are included

**Feature:** F003 | **Routes:** ROUTE011

---

## Metrics

### US009 — View Aggregated Metrics
**As an** authenticated user,  
**I want to** see aggregated social metrics (total likes, shares, comments, etc.),  
**So that** I can assess overall social media performance at a glance.

**Acceptance criteria:**
- GET /metrics returns sum of likes, shares, comments, followers, reach, impressions across all posts

**Feature:** F001 | **Routes:** ROUTE012

---

### US010 — View Last Crawl Timestamp
**As an** authenticated user,  
**I want to** know when metrics were last refreshed,  
**So that** I understand how current the displayed numbers are.

**Acceptance criteria:**
- GET /metrics/last-updated returns the timestamp of the most recent SocialMetric record

**Feature:** F001 | **Routes:** ROUTE013

---

## Background / Platform

### US011 — Automatic Metric Crawl
**As the** system,  
**I want to** automatically fetch metrics from connected social accounts on a schedule,  
**So that** the displayed metrics stay current without user intervention.

**Acceptance criteria:**
- Crawl runs every hour (configurable via app.crawler.rate-ms)
- Each SocialAccount is crawled asynchronously (@Async) to avoid blocking
- A new SocialMetric row is persisted per post per crawl run

**Feature:** F006 | **Trigger:** @Scheduled

---

### US012 — Async Stats Recalculation After Import
**As the** system,  
**I want to** recalculate per-platform post counts asynchronously after a successful import,  
**So that** the dashboard stats are accurate without adding latency to the HTTP import response.

**Acceptance criteria:**
- After each successful import batch DB commit, an ImportCompletedMessage is sent to the JMS queue
- The JMS listener recalculates ACTIVE post counts per platform and updates the in-memory cache
- If the listener fails, the message is retried and eventually routed to DLQ.IMPORT_COMPLETED
- A rolling-back DB transaction does NOT trigger JMS publish (AFTER_COMMIT guard)

**Feature:** F007 | **Trigger:** JMS IMPORT_COMPLETED queue

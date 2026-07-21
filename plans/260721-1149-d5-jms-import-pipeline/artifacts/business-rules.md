# Business Rules

## Import Rules

### BR001 — Validate-First Import
All rows in an uploaded Excel file must be validated before any row is persisted. If any row fails validation, the entire batch is rejected. No partial imports.

**Source:** `ExcelImportService.java`

---

### BR002 — Duplicate Post Rejection
A post with the same `(platform, platform_post_id)` combination cannot be imported if an ACTIVE post with those keys already exists. Soft-deleted posts (status=DELETED) do not block re-import of the same key.

**Source:** `PostRepository.findByStatusAndPlatformAndPlatformPostIdIn`, `V1__init_schema.sql` (partial unique index `uk_platform_post_active`)

---

### BR003 — Import File Size Limit
Uploaded Excel files must not exceed 10 MB. The server rejects larger payloads with a 413 error before reaching the service layer.

**Source:** `application.properties` (`spring.servlet.multipart.max-file-size=10MB`)

---

### BR004 — ImportBatch Status Lifecycle
An ImportBatch transitions through: `PENDING → PROCESSING → DONE` (all rows accepted) or `PENDING → PROCESSING → FAILED` (batch rejected). Terminal states (DONE, FAILED) are not reversible.

**Source:** `entity/ImportBatchStatus.java`, `ExcelImportService.java`

---

## Post Rules

### BR005 — Soft-Delete Only
Posts are never physically deleted. DELETE /posts/{id} sets `status = DELETED`. This preserves the historical record and allows re-import of the same platform_post_id after deletion.

**Source:** `PostService.java`, `entity/PostStatus.java`

---

### BR006 — Only ACTIVE Posts Appear in Lists and Exports
`GET /posts` and `GET /export-report` filter by `status = ACTIVE`. Soft-deleted posts are excluded from all user-facing output.

**Source:** `PostService.java`, `ExcelExportService.java`

---

## Metrics Rules

### BR007 — Latest Metric Snapshot Per Post
Exports include only the most recent `SocialMetric` row per post (by `crawled_at DESC`). Multiple crawl runs accumulate time-series rows; the export takes only the latest snapshot.

**Source:** `SocialMetricRepository.findTopByPostIdOrderByCrawledAtDesc`, `ExcelExportService.java`

---

### BR008 — Metrics Are Refreshed Hourly
The crawl job runs on a fixed-delay schedule (default: every 3,600,000 ms = 1 hour). The delay is counted from the END of the previous crawl run, not from a fixed wall-clock schedule.

**Source:** `CrawlJobService.java` (`@Scheduled(fixedDelayString = "${app.crawler.rate-ms:3600000}")`)

---

## JMS / Async Rules

### BR009 — JMS Publish Only After DB Commit
`ImportEventProducer` publishes the `ImportCompletedMessage` to the JMS queue only after the enclosing DB transaction successfully commits (`@TransactionalEventListener(phase = AFTER_COMMIT)`). A rolling-back transaction does not produce a JMS message.

**Source:** `ImportEventProducer.java`

---

### BR010 — Failed JMS Processing Routes to DLQ
If `ImportEventListener.onImportCompleted()` throws an exception during stats recalculation, the message is NAK'd (Spring JMS re-throws → broker retries). After exhausting `max-delivery-attempts` (Artemis default: 10), the message is routed to `DLQ.IMPORT_COMPLETED`.

**Source:** `ImportEventListener.java`, Artemis broker defaults

---

### BR011 — Stats Cache Reflects Only Completed Imports
`ImportStatsCache` holds the latest `ImportStats` snapshot (totalPosts + per-platform ACTIVE counts). It is populated exclusively by the JMS listener after a successful import. Before the first import, `ImportStatsCache.get()` returns `Optional.empty()`.

**Source:** `ImportStatsCache.java`, `ImportEventListener.java`

---

## Auth Rules

### BR012 — All Protected Endpoints Require Authenticated Session
Any request to a non-public path without an active authenticated session is redirected to `/login`. There are no API keys or token-based auth — session cookie only.

**Source:** `SecurityConfig.java`

---

### BR013 — OAuth2 Login Upserts User and SocialAccount
On successful OAuth2 login, the system finds or creates a `User` record by email (Facebook) or provider_account_id (Twitter). It also upserts the `SocialAccount` record with the latest access token. A single User can have accounts on multiple providers.

**Source:** `CustomOAuth2UserService.java`

---

### BR014 — Twitter Login Does Not Require Email
Twitter's OAuth2 API does not return an email address. The system accepts Twitter logins without an email field; the email column may be null for Twitter users.

**Source:** `CustomOAuth2UserService.java`, `entity/User.java` (email nullable for Twitter)

---

## Pagination Rules

### BR015 — Maximum Page Size Is 100
`GET /posts` enforces a server-side cap of 100 items per page regardless of the `size` query parameter. This prevents unbounded result sets.

**Source:** `application.properties` (`spring.data.web.pageable.max-page-size=100`)

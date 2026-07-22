# API Map

## Route ↔ Feature ↔ Service ↔ Model

| ROUTE### | Path | Feature | Service | Model(s) |
|----------|------|---------|---------|----------|
| ROUTE001 | GET / | F005_Dashboard | — (controller reads SecurityContext) | User |
| ROUTE002 | GET /login | F004_Oauth2SocialLogin | — | — |
| ROUTE003 | GET /oauth2/authorization/facebook | F004_Oauth2SocialLogin | CustomOAuth2UserService | User, SocialAccount |
| ROUTE004 | GET /oauth2/authorization/twitter | F004_Oauth2SocialLogin | CustomOAuth2UserService | User, SocialAccount |
| ROUTE005 | GET /login/oauth2/code/facebook | F004_Oauth2SocialLogin | CustomOAuth2UserService | User, SocialAccount |
| ROUTE006 | GET /login/oauth2/code/twitter | F004_Oauth2SocialLogin | CustomOAuth2UserService | User, SocialAccount |
| ROUTE007 | POST /logout | F004_Oauth2SocialLogin | — | — |
| ROUTE008 | GET /posts | F001_PostMetricTestSuite | PostService | Post |
| ROUTE009 | DELETE /posts/{id} | F001_PostMetricTestSuite | PostService | Post |
| ROUTE010 | POST /import-posts | F002_ExcelBulkImportPosts | ExcelImportService, ImportBatchService | Post, ImportBatch |
| ROUTE011 | GET /export-report | F003_ExcelExportReport | ExcelExportService | Post, SocialMetric |
| ROUTE012 | GET /metrics | F001_PostMetricTestSuite | MetricService | SocialMetric |
| ROUTE013 | GET /metrics/last-updated | F001_PostMetricTestSuite | MetricService | SocialMetric |

## Async / Background Paths (no HTTP route)

| Path | Feature | Trigger | Service | Output |
|------|---------|---------|---------|--------|
| Scheduled crawl | F006_AutomatedCrawlJob | @Scheduled fixedDelay (app.crawler.rate-ms) | CrawlJobService → SocialCrawlerService (@Async) | SocialMetric persisted |
| JMS IMPORT_COMPLETED | F007_JmsImportPipeline | After DB commit of import batch (ImportEventProducer @TransactionalEventListener) | ImportEventListener → ImportStatsCache | In-memory ImportStats updated |

## Request/Response Shapes

### GET /posts
**Query params:** `page` (default 0), `size` (default 20, max 100)
**Response:** `Page<PostResponse>` JSON
```json
{
  "content": [{ "id": 1, "platform": "FACEBOOK", "title": "...", "status": "ACTIVE", ... }],
  "page": { "number": 0, "size": 20, "totalElements": 42, "totalPages": 3 }
}
```

### DELETE /posts/{id}
**Response:** 204 No Content on success; 404 if not found

### POST /import-posts
**Content-Type:** `multipart/form-data`
**Body:** `file` field = .xlsx binary (max 10 MB)
**Response:** `ImportBatchResponse` JSON
```json
{ "batchId": 1, "totalRecords": 100, "successRecords": 98, "failedRecords": 2, "status": "DONE" }
```

### GET /export-report
**Response:** `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` binary (.xlsx download)

### GET /metrics
**Response:** `MetricResponse` JSON
```json
{ "totalPosts": 200, "totalLikes": 5400, "totalShares": 1200, "totalComments": 340 }
```

### GET /metrics/last-updated
**Response:** `LastUpdatedResponse` JSON
```json
{ "lastUpdatedAt": "2026-07-21T09:00:00Z" }
```

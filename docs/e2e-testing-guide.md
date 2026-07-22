# E2E Testing Guide — Social Analytics Dashboard

Hướng dẫn kiểm thử thủ công theo từng ngày phát triển.

---

## Điều kiện chung

```bash
# Khởi động app
./mvnw spring-boot:run

# URL gốc
http://localhost:8080

# Swagger UI
http://localhost:8080/swagger-ui.html
```

Mỗi request cần cookie session (lấy từ browser sau khi login). Swagger UI tự gửi cookie nếu cùng tab.

---

## Day 1 — Project Setup & CRUD APIs

**Mục tiêu:** project compile, DB tạo bảng, CRUD hoạt động, Swagger accessible.

### 1.1 Swagger UI

```
GET http://localhost:8080/swagger-ui.html
```
✅ Trang Swagger render, thấy các group: Posts, Metrics, Charts, ...

### 1.2 GET /posts (paginated)

Swagger → `POST /posts` → Execute (không cần body để test GET)  
Hoặc curl:
```bash
curl -b "JSESSIONID=<cookie>" http://localhost:8080/posts
```
✅ `{"content": [], "totalElements": 0, ...}` — page rỗng là đúng nếu chưa có data.

### 1.3 GET /metrics

```bash
curl -b "JSESSIONID=<cookie>" http://localhost:8080/metrics
```
✅ Response 200, `content` là mảng (có thể rỗng).

### 1.4 Verify DB schema

```sql
\dt  -- psql
-- Phải thấy: posts, social_metrics, users, social_accounts, import_batches
```

---

## Day 2 — Excel Import / Export

**Mục tiêu:** import Excel tạo posts trong DB, export trả về file `.xlsx`.

### 2.1 Chuẩn bị file Excel test

File `test-posts.xlsx` tối thiểu:

| platformPostId | platform | title          | content    | postUrl              |
|----------------|----------|----------------|------------|----------------------|
| post-fb-001    | FACEBOOK | Test Facebook  | Nội dung 1 | https://fb.com/1     |
| post-tw-001    | TWITTER  | Test Twitter   | Nội dung 2 | https://twitter.com/1|

### 2.2 POST /import-posts

Swagger → POST `/import-posts` → chọn file → Execute.

Hoặc curl:
```bash
curl -b "JSESSIONID=<cookie>" \
  -F "file=@test-posts.xlsx" \
  http://localhost:8080/import-posts
```

✅ Response:
```json
{
  "batchId": 1,
  "totalRecords": 2,
  "successCount": 2,
  "failedCount": 0,
  "status": "COMPLETED"
}
```

Xác nhận DB:
```sql
SELECT id, platform, platform_post_id FROM posts ORDER BY id DESC LIMIT 5;
```

### 2.3 GET /export-report

```bash
curl -b "JSESSIONID=<cookie>" \
  -o report.xlsx \
  http://localhost:8080/export-report
```

✅ File `report.xlsx` tải về, mở bằng Excel/LibreOffice thấy header và data rows.

### 2.4 Edge cases

| Test | Hành động | Kết quả mong đợi |
|------|-----------|-----------------|
| File rỗng | Upload file Excel 0 row | `successCount: 0` |
| File sai định dạng | Upload `.txt` | 400 Bad Request |
| File quá lớn (>10MB) | Upload file lớn | 400 "Maximum upload size exceeded" |

---

## Day 3 — Security & OAuth2 Login

**Mục tiêu:** unauthenticated → redirect login; OAuth2 flow hoạt động; CSRF bảo vệ forms.

### 3.1 Unauthenticated access

```bash
curl -v http://localhost:8080/
```
✅ `HTTP/1.1 302` → `Location: http://localhost:8080/login`

```bash
curl -v http://localhost:8080/posts
```
✅ 302 redirect (không phải 200 hay 403).

### 3.2 Login page

```
GET http://localhost:8080/login
```
✅ Trang login render, thấy nút "Login with Facebook" và "Login with Twitter".

### 3.3 OAuth2 flow (với mock credentials)

`application.properties` cần có:
```properties
spring.security.oauth2.client.registration.facebook.client-id=<test-id>
spring.security.oauth2.client.registration.facebook.client-secret=<test-secret>
```

Bấm "Login with Facebook" → redirect sang Facebook authorization URL.

✅ Sau callback thành công: redirect về `/` dashboard, thấy tên và email user.

Xác nhận DB:
```sql
SELECT id, email, name FROM users ORDER BY id DESC LIMIT 1;
SELECT provider, access_token IS NOT NULL FROM social_accounts ORDER BY id DESC LIMIT 1;
```

### 3.4 CSRF protection

```bash
# Thử logout KHÔNG có CSRF token (raw POST)
curl -v -X POST http://localhost:8080/logout
```
✅ 403 Forbidden — CSRF token thiếu.

```bash
# Logout đúng cách (có CSRF token qua form Thymeleaf)
# Bấm nút Logout trên dashboard → redirect về /login?logout
```

### 3.5 Authenticated access sau login

```bash
curl -b "JSESSIONID=<cookie-sau-login>" http://localhost:8080/posts
```
✅ 200, không còn redirect.

---

## Day 4 — Background Jobs & Multithreading

**Mục tiêu:** crawl job tự chạy, metric được lưu vào DB, endpoint last-updated hoạt động.

### 4.1 Giảm interval để test nhanh

```properties
# application.properties
app.crawler.rate-ms=10000
```

Restart app.

### 4.2 Quan sát log crawl job

```bash
./mvnw spring-boot:run 2>&1 | grep "crawl job"
```

✅ Sau ~10s thấy:
```
crawl job started: 2 active posts
crawl job done: success=2 failed=0 elapsed=XXXms
```

### 4.3 Xác nhận SocialMetric được tạo

```sql
SELECT sm.id, p.platform, sm.likes_count, sm.crawled_at
FROM social_metrics sm
JOIN posts p ON sm.post_id = p.id
ORDER BY sm.crawled_at DESC
LIMIT 5;
```
✅ Có rows mới sau mỗi cycle crawl.

### 4.4 GET /metrics/last-updated

```bash
curl -b "JSESSIONID=<cookie>" http://localhost:8080/metrics/last-updated
```
✅ Response:
```json
{"lastCrawledAt": "2026-07-12T10:30:00.123456Z"}
```
Giá trị `null` nếu chưa crawl lần nào.

### 4.5 Dashboard "Last Updated"

Mở `http://localhost:8080` → thấy row "Last Updated" hiển thị timestamp.

---

## Day 5 — JMS + WebSocket + Charts

**Mục tiêu:** JMS pipeline hoạt động, WebSocket kết nối và nhận broadcast, chart cập nhật realtime.

### 5.1 Chuẩn bị

1. Đảm bảo có ít nhất 2 posts trong DB (xem Day 2).
2. Đặt `app.crawler.rate-ms=10000` để crawl mỗi 10s.

### 5.2 Kiểm tra WebSocket

Mở `http://localhost:8080` → DevTools → Console:
```js
const c = new StompJs.Client({ brokerURL: 'ws://localhost:8080/ws' });
c.onConnect = () => console.log('CONNECTED');
c.activate();
```
✅ Log "CONNECTED" trong Console.

Trên Dashboard: dot chuyển xanh, label "Live".

Nếu `StompJs is not defined`:
```bash
# CDN không load được — dùng file local
mkdir -p src/main/resources/static/js
curl -o src/main/resources/static/js/stomp.umd.min.js \
  https://cdn.jsdelivr.net/npm/@stomp/stompjs@7/bundles/stomp.umd.min.js
curl -o src/main/resources/static/js/chart.umd.min.js \
  https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js
```
Đổi `src` trong `dashboard.html` sang `/js/stomp.umd.min.js` và `/js/chart.umd.min.js`.

### 5.3 Trigger 1: Import Excel → JMS broadcast

Import file Excel (xem Day 2.2) → quan sát DevTools → Network → WS → Messages.

✅ Trong vòng 1-2s sau import, thấy WS frame:
```json
{"event": "IMPORT_COMPLETE", "updatedAt": "2026-07-12T10:35:00Z"}
```

Dashboard: `ws-update` bar hiển thị "IMPORT_COMPLETE · 10:35:00".

### 5.4 Trigger 2: Crawl job → WebSocket broadcast

Chờ ~10s (với `rate-ms=10000`) → xem WS frame:
```json
{"event": "CRAWL_COMPLETE", "updatedAt": "2026-07-12T10:35:10Z"}
```

### 5.5 GET /chart-data

```bash
# Không filter
curl -b "JSESSIONID=<cookie>" http://localhost:8080/chart-data

# Filter theo platform
curl -b "JSESSIONID=<cookie>" "http://localhost:8080/chart-data?platform=FACEBOOK"

# Filter theo ngày
curl -b "JSESSIONID=<cookie>" \
  "http://localhost:8080/chart-data?from=2026-07-01T00:00:00Z&to=2026-07-31T23:59:59Z"
```

✅ Response mẫu:
```json
{
  "labels": ["2026-07-12"],
  "datasets": [
    {"platform": "FACEBOOK", "likes": [150], "shares": [30]},
    {"platform": "TWITTER",  "likes": [80],  "shares": [12]}
  ]
}
```

Edge cases:

| Test | Query param | Kết quả mong đợi |
|------|------------|-----------------|
| Platform không tồn tại | `?platform=INSTAGRAM` | 400 `{"error": "No enum constant ..."}` |
| Không có data | DB rỗng | `{"labels": [], "datasets": []}` |

### 5.6 Chart render

Sau WS broadcast, dashboard tự gọi `/chart-data` và render:
- Line chart "Likes over Time" — trục X là ngày, mỗi platform 1 đường
- Bar chart "Shares by Platform" — tổng shares theo platform

✅ Refresh thủ công để xác nhận chart render ngay cả khi không có WS event.

### 5.7 DLQ behavior

Xem log khi import một file lỗi (thiếu cột bắt buộc):
```
Stats recalculation failed for batchId=X: ...
```
✅ Sau số lần retry cấu hình, message chuyển sang `DLQ.IMPORT_COMPLETED`.

Kiểm tra DLQ qua Artemis console (nếu bật):
```
http://localhost:8161/console
```

---

## Day 6 — SOAP + Advanced Reflection + Integration Tests

**Mục tiêu:** SOAP client hoạt động, @ExcelColumn export đúng thứ tự, full test suite xanh.

### 6.1 GET /exchange-rate (SOAP client)

```bash
curl -b "JSESSIONID=<cookie>" "http://localhost:8080/exchange-rate?currency=USD"
```
✅ Response:
```json
{"currency": "USD", "rate": 23500.0}
```
(Giá trị mock, không phải live rate.)

### 6.2 Export với @ExcelColumn annotation

Import vài posts (Day 2.2) → crawl chạy để có SocialMetric → export:
```bash
curl -b "JSESSIONID=<cookie>" \
  -o report-annotated.xlsx \
  http://localhost:8080/export-report
```

Mở file: ✅ Cột xuất hiện đúng thứ tự `order` trong `@ExcelColumn`, header đúng `headerName`.

### 6.3 Chạy toàn bộ test suite

```bash
./mvnw test --no-transfer-progress
```

✅ Output cuối:
```
Tests run: XX, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Coverage ≥ 70%:
```bash
./mvnw test jacoco:report --no-transfer-progress
open target/site/jacoco/index.html
```

### 6.4 Integration test: import → JMS → stats

Xem class `*IntegrationTest` hoặc chạy riêng:
```bash
./mvnw test -Dtest="*IntegrationTest" --no-transfer-progress
```

✅ Test pass: import Excel → post tạo trong DB → `IMPORT_COMPLETED` published → stats cache cập nhật.

---

## Quick Reference — Checklist theo ngày

| Day | Checklist nhanh |
|-----|----------------|
| 1 | `[ ]` Swagger UI load · `[ ]` GET /posts 200 · `[ ]` DB tables tồn tại |
| 2 | `[ ]` Import 2 posts thành công · `[ ]` Export tải file .xlsx · `[ ]` File rỗng → successCount=0 |
| 3 | `[ ]` Unauthenticated → 302 · `[ ]` Login Facebook/Twitter OK · `[ ]` Logout thành công |
| 4 | `[ ]` Log "crawl job done" xuất hiện · `[ ]` social_metrics có rows · `[ ]` /metrics/last-updated không null |
| 5 | `[ ]` WS dot xanh · `[ ]` Import → WS frame "IMPORT_COMPLETE" · `[ ]` Chart render sau broadcast |
| 6 | `[ ]` /exchange-rate 200 · `[ ]` Export cột đúng thứ tự · `[ ]` Test suite 0 failures |

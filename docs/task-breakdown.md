# Task Breakdown — Social Analytics Dashboard (6 Days)

**Start date:** 2026-07-08  
**End date:** 2026-07-13  
**Total working days:** 6

---

## Day 1 — Project Setup & Base Structure
**Date:** 2026-07-08  
**Goal:** Running Spring Boot project with entities, CRUD APIs, and Swagger

### Tasks

- [ ] **D1-01** Initialize Spring Boot 3.x project (`social-analytics`) via Spring Initializr
  - Dependencies: Spring Web, Spring Data JPA, PostgreSQL Driver, Lombok, Validation, Swagger (springdoc-openapi)
- [ ] **D1-02** Configure `application.properties`
  - DB connection (PostgreSQL), JPA DDL auto, logging levels, `hibernate.jdbc.time_zone=UTC`
- [ ] **D1-03** Set up project package structure
  - `controller/`, `service/`, `repository/`, `dto/`, `entity/`, `util/`, `config/`
- [ ] **D1-04** Create JPA entities
  - `User`, `Post`, `SocialMetric`, `SocialAccount`, `ImportBatch`
  - Map relationships, indexes per `database-design.md`
- [ ] **D1-05** Create repositories
  - `UserRepository`, `PostRepository`, `SocialMetricRepository`, `ImportBatchRepository`
- [ ] **D1-06** Implement basic CRUD service + controller for `Post`
  - `GET /posts` (paginated), `DELETE /posts/{id}`
- [ ] **D1-07** Implement basic CRUD service + controller for `SocialMetric`
  - `GET /metrics`
- [ ] **D1-08** Add Swagger / OpenAPI config; verify all endpoints appear in Swagger UI
- [ ] **D1-09** Verify project compiles, Swagger UI accessible at `/swagger-ui.html`

**Deliverables:** Compilable project, DB tables auto-created, CRUD APIs documented in Swagger

---

## Day 2 — Unit Tests + Excel Import/Export
**Date:** 2026-07-09  
**Goal:** Test coverage for services; working Excel import and export APIs

### Tasks

- [x] **D2-01** Add test dependencies: JUnit5, Mockito, MockMvc, H2 (in-memory for tests)
- [x] **D2-02** Write unit tests for `PostService`
  - Test: create, findAll (paginated), delete, not-found exception
  - Tool: Mockito (`@Mock`, `@InjectMocks`)
- [x] **D2-03** Write unit tests for `MetricService`
  - Test: save metric snapshot, findLatestByPostId
- [x] **D2-04** Write `@DataJpaTest` for `PostRepository`
  - Test: custom queries if any
- [x] **D2-05** Write `MockMvc` controller tests for `PostController`
  - Test: 200 GET /posts, 204 DELETE /posts/{id}, 404 on missing post
- [ ] **D2-06** Add Apache POI dependency to `pom.xml`
- [ ] **D2-07** Implement `ExcelImportService`
  - Read Excel file, map rows to `Post` entity using Reflection
  - Return `ImportBatch` summary (total / success / failed)
- [ ] **D2-08** Implement `POST /import-posts` controller
  - Accept `MultipartFile`, delegate to `ExcelImportService`, return summary JSON
- [ ] **D2-09** Implement `ExcelExportService`
  - Generic Reflection-based row writer: read annotated fields from entity class → write columns
- [ ] **D2-10** Implement `GET /export-report` controller
  - Query posts + latest metrics, stream `.xlsx` as download response
- [ ] **D2-11** Write tests for `ExcelImportService` (valid file, missing columns, empty file)

**Deliverables:** ≥70% service coverage, working `/import-posts` and `/export-report`

---

## Day 3 — Security: CSRF + OAuth2 Social Login
**Date:** 2026-07-10  
**Goal:** Secured app with Facebook & Twitter login; tokens persisted

### Tasks

- [ ] **D3-01** Add Spring Security + OAuth2 Client dependencies
- [ ] **D3-02** Implement `SecurityConfig`
  - Enable CSRF, configure permitted paths (`/login`, `/oauth2/**`, `/swagger-ui/**`)
  - Set `oauth2Login()` with success/failure handlers
- [ ] **D3-03** Add CSRF meta tag to all Thymeleaf templates (or configure header-based CSRF for REST)
- [ ] **D3-04** Configure `application.properties` for OAuth2 providers
  - Facebook: `client-id`, `client-secret`, `scope`
  - Twitter: `client-id`, `client-secret`, `scope`
- [ ] **D3-05** Implement `OAuth2UserService` (custom)
  - Extract user info from `OAuth2User` response
  - Upsert `User` + `SocialAccount` (store `access_token`)
- [ ] **D3-06** Implement login page (`/login`) with "Login with Facebook" and "Login with Twitter" buttons
- [ ] **D3-07** Implement post-login redirect → `/` dashboard
- [ ] **D3-08** Verify CSRF token included in forms; test CSRF rejection on tampered requests
- [ ] **D3-09** Write Spring Security integration tests
  - Unauthenticated → 302 to login
  - Authenticated user → 200 on protected endpoints

**Deliverables:** Secured app, OAuth2 login working (mock credentials), tokens in DB

---

## Day 4 — Background Jobs & Multithreading
**Date:** 2026-07-11  
**Goal:** Automated hourly crawl job running on a thread pool; "Last Updated" UI

### Tasks

- [ ] **D4-01** Enable `@EnableAsync` and `@EnableScheduling` in main config
- [ ] **D4-02** Configure `ThreadPoolTaskExecutor` bean
  - Pool size, queue capacity, thread name prefix — all in `application.properties`
- [ ] **D4-03** Implement `SocialCrawlerService`
  - `crawlPost(Post post)` — mock FB/TW API call, return fake metrics, save `SocialMetric`
  - Annotate with `@Async` so each post crawl runs on the thread pool
- [ ] **D4-04** Implement `updateSocialMetricsJob()`
  - Annotated `@Scheduled(fixedRate = 3600000)`
  - Fetches all active posts, calls `crawlPost()` for each (async, parallel)
  - Logs total duration and success/failure counts
- [ ] **D4-05** Add `last_crawled_at` tracking (e.g., in-memory `AtomicReference<Instant>` or DB column)
- [ ] **D4-06** Expose `GET /metrics/last-updated` endpoint returning the last crawl timestamp
- [ ] **D4-07** Add "Last Updated" section to dashboard UI
- [ ] **D4-08** Add exception handling in `@Async` methods (`AsyncUncaughtExceptionHandler`)
- [ ] **D4-09** Write tests for `SocialCrawlerService` (mock the fake API call, verify metric saved)

**Deliverables:** Hourly job running asynchronously, "Last Updated" visible in UI

---

## Day 5 — JMS Messaging + WebSocket + Charts
**Date:** 2026-07-12  
**Goal:** Async JMS pipeline post-import; realtime chart updates via WebSocket

### Tasks

#### JMS (ActiveMQ / RabbitMQ)

- [ ] **D5-01** Add ActiveMQ (or RabbitMQ) dependency; configure broker in `application.properties`
- [ ] **D5-02** Define queue name constant: `IMPORT_COMPLETED`
- [ ] **D5-03** Implement `ImportEventProducer`
  - After Excel import succeeds → publish `ImportCompletedMessage` (batchId, recordCount)
- [ ] **D5-04** Implement `ImportEventListener`
  - `@JmsListener` on `IMPORT_COMPLETED`
  - Recalculate aggregated stats (total posts, per-platform count) and persist/cache
- [ ] **D5-05** Configure DLQ for `IMPORT_COMPLETED`; verify failed messages land in DLQ
- [ ] **D5-06** Test: publish → consume flow (embedded broker or Testcontainers)

#### WebSocket

- [ ] **D5-07** Add Spring WebSocket + Messaging dependencies
- [ ] **D5-08** Configure `WebSocketMessageBrokerConfigurer`
  - Enable STOMP, broker topic `/topic/metrics-update`, app prefix `/app`
- [ ] **D5-09** Implement `MetricsBroadcaster`
  - `SimpMessagingTemplate.convertAndSend("/topic/metrics-update", payload)` after crawl job completes
  - Also broadcast after Excel import listener finishes
- [ ] **D5-10** Implement frontend WebSocket client (SockJS + stomp.js)
  - Subscribe to `/topic/metrics-update`
  - On message: call `GET /chart-data` and refresh chart

#### Charts

- [ ] **D5-11** Implement `GET /chart-data`
  - Returns time-series JSON: `{ labels: [...dates], datasets: [{ platform, likes, shares }] }`
  - Supports query params: `platform`, `from`, `to`
- [ ] **D5-12** Integrate Chart.js on dashboard page
  - Line chart: likes over time
  - Bar chart: shares by platform
- [ ] **D5-13** Test chart data endpoint with various filter combinations

**Deliverables:** JMS pipeline working, charts update in realtime on crawl/import

---

## Day 6 — SOAP WebService + Advanced Reflection + Integration Tests + Wrap-up
**Date:** 2026-07-13  
**Goal:** SOAP client working; Reflection-based export complete; full integration test suite; demo-ready

### Tasks

#### SOAP WebService

- [ ] **D6-01** Add Spring-WS dependency
- [ ] **D6-02** Generate WSDL stubs for mock exchange rate SOAP service (or hand-write simple client)
- [ ] **D6-03** Implement `ExchangeRateWebServiceClient`
  - `getExchangeRate(currency)` → returns mocked rate value
- [ ] **D6-04** Expose `GET /exchange-rate?currency=USD` using the SOAP client
- [ ] **D6-05** Write test for SOAP client (mock `WebServiceTemplate`)

#### Advanced Reflection

- [ ] **D6-06** Implement `@ExcelColumn` custom annotation for entity fields
  - Attributes: `headerName`, `order`, `format`
- [ ] **D6-07** Refactor `ExcelExportService` to use `@ExcelColumn` reflection scan
  - Auto-discover annotated fields → build headers + rows dynamically
- [ ] **D6-08** Apply `@ExcelColumn` to `Post` and `SocialMetric`; verify export output

#### Integration Tests

- [ ] **D6-09** Write full integration test: upload Excel → verify posts in DB → verify `IMPORT_COMPLETED` published → verify aggregated stats updated
- [ ] **D6-10** Write integration test: crawl job triggers → verify new `SocialMetric` row saved → verify WebSocket broadcast
- [ ] **D6-11** Run full test suite; fix any failures; confirm coverage ≥ 70%

#### Final Polish

- [ ] **D6-12** Review and finalize Swagger documentation for all endpoints
- [ ] **D6-13** Run end-to-end demo flow:
  1. Login with Facebook (mock)
  2. Import Excel file
  3. Watch chart update via WebSocket
  4. Export report
  5. View SOAP exchange rate
- [ ] **D6-14** Update `README.md` with setup instructions and demo steps
- [ ] **D6-15** Package JAR (`./mvnw clean package`); verify it runs cleanly

**Deliverables:** All features integrated, tests passing, project demo-ready

---

## Summary Table

| Day | Date       | Focus                            | Key Deliverables                              |
|-----|-----------|----------------------------------|-----------------------------------------------|
| 1   | 2026-07-08 | Project setup & base structure   | Entities, CRUD APIs, Swagger                  |
| 2   | 2026-07-09 | Unit tests + Excel import/export | Test coverage ≥70%, `/import-posts`, `/export-report` |
| 3   | 2026-07-10 | Security: CSRF + OAuth2 login    | Secured app, FB/TW login, tokens persisted    |
| 4   | 2026-07-11 | Background jobs + multithreading | Hourly crawl job, async thread pool, Last Updated UI |
| 5   | 2026-07-12 | JMS + WebSocket + Charts         | JMS pipeline, realtime chart updates          |
| 6   | 2026-07-13 | SOAP + Reflection + final tests  | SOAP client, generic export, end-to-end demo  |

---

## Task Count

| Day | Tasks | Est. Complexity |
|-----|-------|----------------|
| 1   | 9     | Medium          |
| 2   | 11    | Medium-High     |
| 3   | 9     | High            |
| 4   | 9     | High            |
| 5   | 13    | High            |
| 6   | 15    | Very High       |
| **Total** | **66** | |

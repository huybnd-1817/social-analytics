# Project Requirements — Social Analytics Dashboard

## 1. Project Overview

**Name:** Social Analytics Dashboard  
**Type:** Admin web application  
**Purpose:** Aggregate and monitor social engagement metrics (likes, shares, followers) from Facebook & Twitter in one dashboard.  
**Primary User:** Admin

---

## 2. Functional Requirements

### 2.1 Authentication & Social Login

| ID   | Requirement                                                                 |
|------|-----------------------------------------------------------------------------|
| F-01 | Admin can log in via Facebook OAuth2                                        |
| F-02 | Admin can log in via Twitter OAuth2                                         |
| F-03 | System redirects to dashboard after successful login                        |
| F-04 | OAuth2 access token and user info are persisted to the database             |
| F-05 | All forms protected by CSRF tokens                                          |
| F-06 | Unauthenticated requests redirect to login page                             |

### 2.2 Post Management

| ID   | Requirement                                                                 |
|------|-----------------------------------------------------------------------------|
| F-07 | Admin can view a paginated list of all imported posts                       |
| F-08 | Admin can upload an Excel file containing a list of posts (`POST /import-posts`) |
| F-09 | System parses each row in the Excel and persists valid posts to DB          |
| F-10 | System reports import summary: total / success / failed records             |
| F-11 | Admin can delete a post from the dashboard                                  |

**Excel Import Format (columns):**

| Column         | Required | Notes                                |
|---------------|----------|--------------------------------------|
| platform       | Yes      | FACEBOOK or TWITTER                  |
| platform_post_id | Yes    | Original post ID on the platform     |
| title          | No       | Post caption/title                   |
| content        | No       | Post body                            |
| post_url       | No       | Direct URL to the post               |
| published_at   | No       | ISO 8601 datetime string             |

### 2.3 Metrics Crawl & Background Job

| ID   | Requirement                                                                 |
|------|-----------------------------------------------------------------------------|
| F-12 | System automatically crawls metrics from FB/TW every 1 hour via `@Scheduled` |
| F-13 | Each crawl stores a new `SocialMetric` snapshot per post                    |
| F-14 | Crawl job processes multiple accounts concurrently via `ThreadPoolTaskExecutor` |
| F-15 | Dashboard displays "Last Updated" timestamp for each metric snapshot        |
| F-16 | Crawl errors are logged; failed posts do not block others                   |

### 2.4 Analytics Dashboard & Charts

| ID   | Requirement                                                                 |
|------|-----------------------------------------------------------------------------|
| F-17 | Dashboard shows aggregated metrics: total likes, shares, comments, followers |
| F-18 | `GET /chart-data` returns time-series data for Chart.js rendering           |
| F-19 | Charts update in realtime when a new crawl completes (via WebSocket)        |
| F-20 | Admin can filter chart data by platform (Facebook / Twitter)                |
| F-21 | Admin can filter chart data by date range                                   |

### 2.5 Realtime Notifications (WebSocket)

| ID   | Requirement                                                                 |
|------|-----------------------------------------------------------------------------|
| F-22 | Server pushes a WebSocket event when a metrics crawl job completes          |
| F-23 | Frontend subscribes via SockJS + STOMP and refreshes chart automatically    |
| F-24 | Server pushes a WebSocket event when an Excel import completes              |

### 2.6 Export

| ID   | Requirement                                                                 |
|------|-----------------------------------------------------------------------------|
| F-25 | Admin can export current metrics report to Excel (`GET /export-report`)     |
| F-26 | Export uses Apache POI with Reflection-based generic model mapping          |
| F-27 | Exported file includes: post info + latest metric snapshot per post         |

### 2.7 Messaging (JMS)

| ID   | Requirement                                                                 |
|------|-----------------------------------------------------------------------------|
| F-28 | After Excel import completes → publish `IMPORT_COMPLETED` message to queue |
| F-29 | JMS Listener receives message → asynchronously updates aggregated stats     |
| F-30 | Failed messages handled via Dead Letter Queue (DLQ)                         |

### 2.8 SOAP WebService Integration

| ID   | Requirement                                                                 |
|------|-----------------------------------------------------------------------------|
| F-31 | System integrates with a mock SOAP WebService (e.g., exchange rate API)     |
| F-32 | SOAP client built with Spring-WS                                            |

---

## 3. Non-Functional Requirements

| ID    | Category       | Requirement                                                          |
|-------|---------------|----------------------------------------------------------------------|
| NF-01 | Security       | CSRF protection on all state-mutating endpoints                      |
| NF-02 | Security       | OAuth2 tokens encrypted at rest                                      |
| NF-03 | Security       | No secrets in source code; use `application.properties` / env vars   |
| NF-04 | Performance    | Background crawl job does not block the main request thread          |
| NF-05 | Performance    | Excel import supports files up to 10,000 rows without timeout        |
| NF-06 | Reliability    | JMS messages retried on failure; DLQ captures exhausted messages     |
| NF-07 | Observability  | All background jobs and exceptions logged via SLF4J + Logback        |
| NF-08 | Maintainability| Code coverage ≥ 70% via JUnit5 + Mockito                            |
| NF-09 | API            | All REST endpoints documented via Swagger / OpenAPI                  |
| NF-10 | Scalability    | `ThreadPoolTaskExecutor` pool size configurable in `application.properties` |

---

## 4. Tech Stack

| Category        | Technology                                  |
|----------------|---------------------------------------------|
| Framework       | Spring Boot 3.x                             |
| Security        | Spring Security + OAuth2 Login + CSRF       |
| Database        | PostgreSQL + Spring Data JPA                |
| Messaging       | JMS — ActiveMQ or RabbitMQ                  |
| Realtime        | WebSocket + STOMP (SockJS on client)        |
| Scheduler       | `@Scheduled` + `@Async` + `@EnableAsync`    |
| Multithread     | `ThreadPoolTaskExecutor`                    |
| File Handling   | Apache POI (Excel import/export)            |
| WebService      | SOAP — Spring-WS                            |
| Charts          | Chart.js or ECharts                         |
| Testing         | JUnit5 + Mockito + MockMvc + DataJpaTest    |
| API Docs        | Swagger / OpenAPI                           |
| Logging         | SLF4J + Logback                             |
| Frontend        | Thymeleaf or ReactJS (minimal UI)           |
| Build           | Maven or Gradle                             |

---

## 5. API Endpoints Summary

| Method | Path              | Description                          | Auth Required |
|--------|------------------|--------------------------------------|---------------|
| GET    | `/`               | Dashboard home                       | Yes           |
| GET    | `/login`          | Login page                           | No            |
| GET    | `/oauth2/callback`| OAuth2 redirect handler              | No            |
| GET    | `/posts`          | List all posts (paginated)           | Yes           |
| DELETE | `/posts/{id}`     | Delete a post                        | Yes           |
| POST   | `/import-posts`   | Upload Excel to import posts         | Yes           |
| GET    | `/export-report`  | Download metrics as Excel            | Yes           |
| GET    | `/chart-data`     | Aggregated metrics for charts        | Yes           |
| GET    | `/metrics`        | Raw metrics list                     | Yes           |
| WS     | `/ws`             | WebSocket endpoint (STOMP)           | Yes           |

---

## 6. Architecture Overview

```
Browser (SockJS + Chart.js)
        │
        ▼
Spring Boot App
├── SecurityConfig (OAuth2 + CSRF)
├── Controllers (REST + WebSocket)
├── Services
│   ├── PostService
│   ├── MetricService
│   ├── ExcelService (Apache POI + Reflection)
│   └── SocialCrawlerService (@Async)
├── Repositories (Spring Data JPA)
├── Scheduler (updateSocialMetricsJob — @Scheduled)
├── JMS Producer/Consumer (IMPORT_COMPLETED queue)
├── WebSocket Broadcaster (STOMP)
└── SOAP Client (Spring-WS)
        │
        ▼
PostgreSQL
```

---

## 7. Constraints & Assumptions

- Facebook and Twitter API calls are **mocked** in this training project; no real API keys required.
- SOAP WebService consumer calls a **mock** exchange rate endpoint.
- Admin role is assigned at account creation; no user self-registration flow.
- Metrics crawl interval (1 hour) is configurable via `application.properties`.

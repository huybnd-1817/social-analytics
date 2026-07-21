# System Overview

## What It Is

Social Analytics is a Spring Boot web application that lets authenticated users **bulk-import social media posts from Excel**, **browse and soft-delete posts**, **export reports**, and **monitor social engagement metrics**. A background crawl job automatically refreshes metrics from the social platforms. An async JMS pipeline keeps in-memory stats up to date after each import — without blocking the HTTP response.

## Who Uses It

Authenticated users (social media managers, analysts) who have linked their Facebook or Twitter/X account via OAuth2 login.

## Core Capabilities

| Capability | Entry Point | Notes |
|------------|-------------|-------|
| Social login | GET /login → OAuth2 | Facebook and Twitter/X; upserts User + SocialAccount |
| Bulk import posts | POST /import-posts | .xlsx upload; validate-first; all-or-nothing per batch |
| Browse & soft-delete posts | GET /posts, DELETE /posts/{id} | Paginated; soft-delete only |
| Export report | GET /export-report | ACTIVE posts + latest metrics snapshot per post |
| View aggregated metrics | GET /metrics | Sum of likes, shares, comments, etc. |
| Auto-crawl metrics | @Scheduled (hourly) | @Async per-account; writes SocialMetric rows |
| Async stats update | JMS IMPORT_COMPLETED | Post-import stats recalculation off the hot path |

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 26 |
| Framework | Spring Boot 4.1.0 / Spring Framework 7 |
| Security | Spring Security 7 + OAuth2 authorization_code (PKCE) |
| Persistence | Spring Data JPA / Hibernate 6 + PostgreSQL |
| Schema migration | Flyway |
| Messaging | ActiveMQ Artemis (embedded, in-VM) + Spring JMS |
| View layer | Thymeleaf (login + dashboard pages only) |
| API docs | springdoc-openapi 3 (Swagger UI, dev-only) |
| Excel I/O | Apache POI (import + export) |
| Build | Maven |
| Test | JUnit 5, Mockito, H2 (in-memory), Awaitility |

## Deployment Profiles

| Profile | DB | JMS | Swagger | Use |
|---------|-----|-----|---------|-----|
| dev | PostgreSQL (local) | Artemis embedded | Enabled | Local development |
| prod | PostgreSQL (env vars) | Artemis embedded | Disabled | Production |
| test | H2 in-memory | Artemis embedded | — | CI / unit + integration tests |

## Security Model

All endpoints require an authenticated session except: `/login`, `/oauth2/**`, `/login/oauth2/**`, Swagger UI paths, and static assets. CSRF protection is active on state-changing endpoints. Sessions are managed by Spring Security (server-side).

## Data Flow (Import)

```
User uploads .xlsx
  → POST /import-posts
  → ExcelImportService: validate rows → persist Posts + ImportBatch (DB transaction)
  → @TransactionalEventListener(AFTER_COMMIT): publish ImportCompletedMessage to JMS
  → ImportEventListener: recount ACTIVE posts per platform → update ImportStatsCache
  → HTTP response returned to user (async — JMS listener does not block response)
```

## Data Flow (Metrics Crawl)

```
@Scheduled (hourly)
  → CrawlJobService: iterate SocialAccounts
  → SocialCrawlerService.crawl() (@Async, per account)
  → Fetch metrics from Facebook/Twitter API
  → Persist SocialMetric row
```

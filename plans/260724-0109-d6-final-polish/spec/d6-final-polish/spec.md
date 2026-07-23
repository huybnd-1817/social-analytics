---
feature: d6-final-polish
lang: en
version: "1.0"
---

# Spec: D6-12 to D6-15 — Final Polish

## Overview

Complete the final four tasks that make the Social Analytics Dashboard project demo-ready:
- **D6-12**: Finalize Swagger/OpenAPI documentation across all endpoints
- **D6-13**: Verify end-to-end demo flow (login → import → chart → export → exchange rate)
- **D6-14**: Create `README.md` with setup instructions and demo steps
- **D6-15**: Package JAR and verify it runs cleanly

---

## D6-12: Swagger Documentation Finalization

### Gap Analysis

Controllers audited; all have `@Tag` + `@Operation`. The following gaps exist:

| Controller | Endpoint | Missing |
|-----------|---------|---------|
| `PostController` | `DELETE /posts/{id}` | `@ApiResponse(404)`, `@ApiResponse(204)` |
| `ChartController` | `GET /chart-data` | `@ApiResponse(400)`, `@Parameter` for `platform`, `from`, `to`, `timezone` |
| `ImportController` | `POST /import-posts` | `@ApiResponse(400)`, `@Parameter` for `file` |
| `ExchangeRateController` | `GET /exchange-rate` | `@Parameter` for `currency` |
| `ExcelExportController` | `GET /export-report` | `@ApiResponse` content type hint for binary download |
| `MetricController` | `GET /metrics/last-updated` | `@ApiResponse(200)` explicit |
| `OpenApiConfig` | — | Update version to match pom.xml `0.0.1-SNAPSHOT` |

### Changes

1. Add `@ApiResponse` annotations to document 200/204/400/404/500 outcomes per endpoint
2. Add `@Parameter` descriptions to `currency`, `platform`, `from`, `to`, `timezone` params
3. Add `@Content` type hint for the binary download in `ExcelExportController`
4. Keep changes surgical — do NOT refactor controller logic

---

## D6-13: E2E Demo Flow

The demo flow is a manual verification sequence:

1. **Login**: Navigate to `http://localhost:8080/login` → click "Login with Facebook"
2. **Import**: `POST /import-posts` with sample `.xlsx` file
3. **Chart update**: WebSocket auto-pushes after import; chart refreshes
4. **Export**: `GET /export-report` downloads `.xlsx`
5. **Exchange rate**: `GET /exchange-rate?currency=USD` returns VND rate

This task is documented in README.md (D6-14) and verified as part of D6-15 (build passes, integration tests green).

---

## D6-14: README.md

Content structure:
- **Project Overview**: what the app does, tech stack
- **Prerequisites**: Java 21, Maven, PostgreSQL 14+, (optional) Facebook/Twitter OAuth2 creds
- **Quick Start**: clone → configure `.env` → start DB → run migrations → `./mvnw spring-boot:run`
- **Demo Flow**: step-by-step numbered list (mirrors D6-13 above)
- **API Documentation**: link to Swagger UI at `http://localhost:8080/swagger-ui.html`
- **Running Tests**: `./mvnw test`
- **Tech Stack**: Spring Boot 3, Spring Security + OAuth2, JPA/Postgres, ActiveMQ, WebSocket, POI, Spring-WS

---

## D6-15: Package JAR

Command: `./mvnw clean package`

Acceptance criteria:
- Build exits with code 0
- JAR file present at `target/social-analytics-*.jar`
- No compilation errors, no test failures during package

---

## Acceptance Criteria (aggregate)

- [ ] All Swagger annotations compile; Swagger UI renders all endpoints cleanly
- [ ] `README.md` exists at repo root, covers setup + demo steps
- [ ] `./mvnw clean package` exits 0; JAR artifact present
- [ ] No regressions in existing tests

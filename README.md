# Social Analytics Dashboard

A Spring Boot 3 web application that aggregates social engagement metrics (likes, shares, followers) from Facebook and Twitter into a single dashboard. Supports OAuth2 login, Excel import/export, real-time chart updates via WebSocket, scheduled background crawls, JMS messaging, and SOAP exchange-rate lookup.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.4 |
| Security | Spring Security + OAuth2 Client (Facebook, Twitter) |
| Persistence | Spring Data JPA + PostgreSQL + Flyway |
| Messaging | Apache ActiveMQ (JMS) |
| Real-time | Spring WebSocket + STOMP |
| Excel | Apache POI (import & export via Reflection) |
| Web Service | Spring-WS (SOAP client) |
| Docs | springdoc-openapi (Swagger UI) |
| Tests | JUnit 5, Mockito, Testcontainers, MockMvc |

## Prerequisites

- **Java 21** (or later)
- **Maven 3.9+** (or use the included `./mvnw` wrapper)
- **PostgreSQL 14+** running locally (default: `localhost:5432/social_analytics`)
- **OAuth2 app credentials** (optional — needed only for Facebook/Twitter login; all other endpoints work without it)

## Quick Start

### 1. Clone the repository

```bash
git clone <repo-url>
cd social-analytics
```

### 2. Configure environment

Create a `.env` file in the project root (it is `.gitignore`d):

```env
FACEBOOK_CLIENT_ID=<your-meta-app-id>
FACEBOOK_CLIENT_SECRET=<your-meta-app-secret>
TWITTER_CLIENT_ID=<your-twitter-oauth2-client-id>
TWITTER_CLIENT_SECRET=<your-twitter-oauth2-client-secret>
```

> **Skip this step** if you only want to test REST endpoints via Swagger UI — OAuth2 login is not required for `/import-posts`, `/export-report`, `/chart-data`, `/exchange-rate`, or `/metrics`.

### 3. Start PostgreSQL and create the database

```bash
psql -U postgres -c "CREATE DATABASE social_analytics;"
```

### 4. Run the application

```bash
./mvnw spring-boot:run
```

Flyway runs migrations automatically on startup. The app listens on **http://localhost:8080**.

## Running Tests

```bash
./mvnw test
```

Integration tests use an embedded ActiveMQ broker and Testcontainers (PostgreSQL). Docker must be running for Testcontainers tests.

## End-to-End Demo Flow

1. **Login** — Navigate to `http://localhost:8080/login` and click **Login with Facebook** (requires OAuth2 credentials in `.env`).

2. **Import posts** — From the dashboard, upload a `.xlsx` file via the **Import** button, or call the API directly:
   ```bash
   curl -X POST http://localhost:8080/import-posts \
     -F "file=@sample-posts.xlsx"
   ```
   The response includes `batchId`, `totalRecords`, `successRecords`, `failedRecords`, and `status`.

3. **Watch chart update** — After a successful import, the JMS listener recalculates stats and a WebSocket broadcast refreshes the dashboard charts automatically.

4. **Export report** — Download the current report as an Excel file:
   ```bash
   curl -OJ http://localhost:8080/export-report
   ```

5. **View SOAP exchange rate** — Query the mocked SOAP exchange-rate service:
   ```bash
   curl "http://localhost:8080/exchange-rate?currency=USD"
   ```

## API Documentation

Interactive Swagger UI is available at:

```
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON spec:

```
http://localhost:8080/v3/api-docs
```

## Package as JAR

```bash
./mvnw clean package
```

The executable JAR is produced at `target/social-analytics-*.jar`. Run it with:

```bash
java -jar target/social-analytics-*.jar
```

Set the active profile and OAuth2 credentials via environment variables before running in non-dev environments:

```bash
export SPRING_PROFILES_ACTIVE=prod
export FACEBOOK_CLIENT_ID=...
java -jar target/social-analytics-*.jar
```

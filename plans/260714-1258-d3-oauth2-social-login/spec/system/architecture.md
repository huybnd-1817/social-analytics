---
status: draft
authored_by: takumi
created: 2026-07-14
lang: en
---

# System Architecture — Security Layer & OAuth2 Client Integration

## Overview

This draft describes the security layer and OAuth2 client integration that D3 adds to the existing Social Analytics Dashboard. It covers how the new `SecurityConfig` filter chain, the custom `OAuth2UserService`, and the two provider integrations (Facebook and Twitter/X) sit within the existing layered architecture.

## Existing Architecture (pre-D3)

The application follows a standard Spring Boot layered architecture:

```
HTTP Request
    │
    ▼
Controller layer  (@RestController — PostController, MetricController, ImportController, ExportController)
    │
    ▼
Service layer     (PostService, MetricService, ExcelImportService, ExcelExportService, …)
    │
    ▼
Repository layer  (Spring Data JPA — UserRepository, PostRepository, SocialAccountRepository, …)
    │
    ▼
Database          (PostgreSQL via Flyway-managed schema; ddl-auto=none)
```

All endpoints are `@RestController` (JSON). No security filter, no templates, no session management existed before D3.

## D3 Additions: Security Filter Chain

D3 inserts a Spring Security `SecurityFilterChain` **in front of** the controller layer. Every inbound HTTP request passes through the filter chain before reaching any controller.

```
HTTP Request
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│  Spring Security Filter Chain  (SecurityConfig @Bean)    │
│                                                          │
│  1. CsrfFilter          — validates _csrf token          │
│  2. OAuth2LoginAuthFilter — intercepts /oauth2/auth/**   │
│  3. OAuth2CallbackFilter  — intercepts /login/oauth2/**  │
│  4. AuthorizationFilter   — enforces authenticated()     │
│     • Permitted: /login, /oauth2/**, /login/oauth2/**,   │
│       /swagger-ui/**, /v3/api-docs/**, static assets,    │
│       /error                                             │
│     • Everything else → requires authenticated session   │
└─────────────────────────────────────────────────────────┘
    │                           │
    │ (permitted / authed)      │ (unauthenticated protected path)
    ▼                           ▼
Controller layer           Redirect → /login
```

## OAuth2 Client Integration

Spring Security manages the full OAuth2 authorization code flow. Two external providers are registered:

```
Browser                 Spring Security              Facebook / Twitter API
  │                          │                              │
  │── click provider btn ───►│                              │
  │                          │── redirect to provider ─────►│
  │◄─── 302 to provider ────│                              │
  │                          │                              │
  │── authorize at provider ─────────────────────────────►│
  │◄─── 302 /login/oauth2/code/{registrationId} ─────────│
  │                          │                              │
  │── GET /login/oauth2/… ──►│                              │
  │                          │── token exchange ───────────►│
  │                          │◄─── access token ───────────│
  │                          │── fetch user-info ──────────►│
  │                          │◄─── user attributes ─────────│
  │                          │                              │
  │                          │── CustomOAuth2UserService    │
  │                          │   .loadUser()                │
  │                          │     • unwrap Twitter data{}  │
  │                          │     • upsert User (role=USER)│
  │                          │     • upsert SocialAccount   │
  │                          │     • store access token     │
  │                          │                              │
  │◄─── 302 / (dashboard) ──│                              │
```

## Key New Components

| Component | Type | Responsibility |
|-----------|------|----------------|
| `SecurityConfig` | `@Configuration @EnableWebSecurity` | Defines the `SecurityFilterChain` bean: permitted paths, `oauth2Login()` DSL, CSRF config (default `HttpSessionCsrfTokenRepository` + `XorCsrfTokenRequestAttributeHandler`), success/failure handlers, logout config |
| `CustomOAuth2UserService` | `@Service extends DefaultOAuth2UserService` | Overrides `loadUser()`: handles provider dispatch, unwraps Twitter nested response, upserts `User` + `SocialAccount`, enforces `role=USER` on new users |
| `login.html` | Thymeleaf template | Login screen at `/login`: two provider anchor buttons, conditional error/logout message band; auto-receives `_csrf` token via `th:action` on any forms |
| `application.yml` additions | Configuration | OAuth2 provider registrations (Facebook via `CommonOAuth2Provider`, Twitter manual); placeholder client credentials via environment variable substitution |

## Thymeleaf Integration

D3 adds `spring-boot-starter-thymeleaf` and `thymeleaf-extras-springsecurity6` (version managed by Spring Boot 4.1 BOM). Thymeleaf templates rendered server-side automatically receive the CSRF token injected into `th:action` forms. The login page uses `<a>` links (not forms) for provider buttons — no CSRF token needed for those GET-initiated redirects.

## CSRF Strategy

- **Token store:** default `HttpSessionCsrfTokenRepository` (server-side session) — appropriate for server-rendered Thymeleaf; no cookie-based store needed.
- **Request handler:** `XorCsrfTokenRequestAttributeHandler` (BREACH protection — Spring Security 7 default).
- **Exempt paths:** OAuth2 callback paths (`/login/oauth2/**`) are GET requests — Spring Security exempts GET/HEAD/OPTIONS/TRACE by default.
- **REST API impact:** All existing REST endpoints (POST `/import-posts`, DELETE `/posts/{id}`) now require a valid CSRF token. Swagger try-it-out will receive 403 unless CSRF is disabled for API paths or Swagger sends the token — this is a known trade-off; resolution is deferred.

## Session Management

Spring Security's default `HttpSessionSecurityContextRepository` stores the authenticated `SecurityContext` in the HTTP session (JSESSIONID cookie). No Redis or JDBC session store is introduced in D3. Session invalidation on logout is handled by the `logout()` DSL.

## Integration with Existing Repositories

`CustomOAuth2UserService` calls two existing repositories:
- `UserRepository.findByEmail(String)` — locate existing user by email
- `SocialAccountRepository.findByProviderAndProviderAccountId(SocialProvider, String)` — locate existing social account for upsert

No new repositories or service classes are introduced for the security layer itself.

## Deferred / Out-of-Scope

- Token encryption at rest (Jasypt/AES) — deferred post-D3; plaintext storage is the D3 interim state.
- X-Forwarded-For trust policy — `server.forward-headers-strategy=framework` is already set; relevant when rate-limiting or auth auditing lands.
- Redis/JDBC session clustering — not needed for single-instance D3 scope.
- Dashboard controller and Thymeleaf template for `/` — D4/D5 scope; D3 success handler redirects to `/` which will 404 until D4.

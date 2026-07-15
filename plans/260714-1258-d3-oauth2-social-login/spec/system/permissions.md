---
status: draft
authored_by: takumi
created: 2026-07-14
lang: en
---

# System Permissions — Authentication & Path Authorization

## Overview

D3 introduces the first access-control layer for the Social Analytics Dashboard. Prior to D3 all paths are publicly accessible. After D3 a Spring Security filter chain enforces two tiers: a permitted set (no authentication required) and a protected set (authenticated session required). Role-based distinctions within the authenticated tier are minimal in D3 — the application does not yet use roles to gate individual features.

## Roles

| Role | How Assigned | Description |
|------|-------------|-------------|
| USER | Explicitly set by `CustomOAuth2UserService` during OAuth2 upsert | Standard authenticated user; assigned to every social-login-created account regardless of the `users.role` DB column default (which is `ADMIN` — a fail-open trap that must be overridden in code) |
| ADMIN | Out-of-band only (direct DB update or future admin provisioning flow) | Not granted through social login; not used to gate any UI feature in D3 scope |

**Critical constraint:** The `users.role` column default is `ADMIN` in the Flyway schema. The `CustomOAuth2UserService` MUST set `role = UserRole.USER` explicitly on every new `User` record. Relying on the DB default is a security fail-open — it would grant every social-login user admin privileges silently.

## Path Authorization Matrix

| Path Pattern | Method(s) | Auth Required | Notes |
|-------------|-----------|--------------|-------|
| `/login` | GET | No | Login page — publicly accessible |
| `/login/**` | GET | No | Spring Security login processing paths |
| `/oauth2/**` | GET | No | OAuth2 authorization initiation (`/oauth2/authorization/{registrationId}`) |
| `/login/oauth2/**` | GET | No | OAuth2 callback (`/login/oauth2/code/{registrationId}`) |
| `/logout` | GET | No (permit all) | Spring Security logout; invalidates session |
| `/swagger-ui/**` | GET | No | Swagger UI static resources |
| `/swagger-ui.html` | GET | No | Swagger UI entry point |
| `/v3/api-docs/**` | GET | No | OpenAPI spec endpoint |
| `/css/**`, `/js/**`, `/images/**`, `/webjars/**` | GET | No | Static assets |
| `/error` | any | No | Spring error page |
| `/**` (all other paths) | any | Yes — authenticated session | Includes all existing REST endpoints: `/posts`, `/metrics`, `/import-posts`, `/export-report` |

## CSRF Policy

| Aspect | Policy |
|--------|--------|
| Enabled | Yes — enforced for all state-mutating requests (POST, PUT, PATCH, DELETE) |
| Token store | `HttpSessionCsrfTokenRepository` (default — session-backed) |
| Request handler | `XorCsrfTokenRequestAttributeHandler` (BREACH protection; Spring Security 7 default) |
| Thymeleaf integration | `th:action` on Thymeleaf forms auto-injects the `_csrf` hidden field when `thymeleaf-extras-springsecurity6` is active |
| Exempt by default | GET, HEAD, OPTIONS, TRACE requests (Spring Security safe-method exemption) |
| OAuth2 callbacks | Exempt — handled as GET by the OAuth2 filter before CSRF filter evaluates |
| Known gap | REST API endpoints (e.g., POST `/import-posts`) now require CSRF token; Swagger try-it-out will receive 403 unless the CSRF token is included. Mitigation deferred — may require disabling CSRF for `/api/**` or a separate `SecurityFilterChain` for REST paths in a future iteration. |

## OAuth2 Upsert Permission Contract

When a social login completes:

1. `CustomOAuth2UserService.loadUser()` is called within the Spring Security authentication flow.
2. The service locates or creates a `User` record — always setting `role = UserRole.USER`.
3. The service locates or creates a `SocialAccount` record keyed on `(provider, providerAccountId)`.
4. No role escalation is possible through the OAuth2 flow — `UserRole.ADMIN` is never assigned by the service.
5. Access token is stored in `SocialAccount.accessToken` (plaintext — encryption deferred; see architecture.md § Deferred).

## Unresolved Questions

- **CSRF + REST API:** Existing REST endpoints (`/import-posts`, `/export-report`, `/posts`, `/metrics`) are now behind both auth and CSRF. A separate `SecurityFilterChain` with lower `@Order` for API paths (with CSRF disabled, stateless session) may be needed when these endpoints are consumed programmatically. This is out of scope for D3 but should be designed before D4 integration work.
- **Admin provisioning path:** No mechanism exists to grant `ADMIN` role to a user through the application. If admin features are planned in D4+, a provisioning mechanism must be designed.
- **PERM### codes:** Not fabricated here — allocated by the Core reconcile pass after implementation. All permission rules above are described in plain rationale form.

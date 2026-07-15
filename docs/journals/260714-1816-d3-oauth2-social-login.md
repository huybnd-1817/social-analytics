# D3 — Spring Security + OAuth2 Social Login Delivery

**Date**: 2026-07-14  
**Branch**: feature/social-login  
**Component**: SecurityConfig, CustomOAuth2UserService, LoginController, login.html, V2 migration  
**Status**: Delivered — all D3 tasks complete

## What Was Delivered

Full Spring Security 7 + OAuth2 social login layer for the Social Analytics Dashboard.

**New production files:**
- `src/main/java/.../config/SecurityConfig.java` — single `SecurityFilterChain` bean; CSRF enabled (default `HttpSessionCsrfTokenRepository` + `XorCsrfTokenRequestAttributeHandler`); permitted paths; `oauth2Login()` DSL with success/failure handlers; logout config
- `src/main/java/.../security/CustomOAuth2UserService.java` — extends `DefaultOAuth2UserService`; provider dispatch; Twitter nested `{"data":{…}}` unwrap; upsert `User` (role=USER) + `SocialAccount` (access token stored); BR-002 enforced (token never logged)
- `src/main/java/.../controller/LoginController.java` — serves `/login` page
- `src/main/resources/templates/login.html` — Thymeleaf login page; Facebook + Twitter/X provider buttons; conditional error/logout message band
- `src/main/resources/db/migration/V2__update_user_role_default.sql` — role column default ADMIN→USER (belt-and-suspenders alongside service-layer override)

**Configuration additions (application.properties):**
- Facebook via `CommonOAuth2Provider` built-in; `scope=public_profile,email`; credentials via env vars with placeholder fallback
- Twitter/X manual registration; `client-authentication-method=client-secret-post`; PKCE auto-enabled by Spring Security 7; `user-name-attribute=data` to handle nested response

**Test files:**
- `SecurityConfigTest.java` — `@WebMvcTest` with `@Import(SecurityConfig.class)`; verifies unauthenticated redirect, permitted paths pass, authenticated access succeeds
- `CustomOAuth2UserServiceTest.java` — verifies upsert logic, role assignment, token persistence, Twitter unwrap path

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Filter chain count | Single chain | Simpler; REST CSRF gap acceptable at D3 scope — deferred to D4 |
| CSRF store | `HttpSessionCsrfTokenRepository` | Default for server-rendered Thymeleaf; no cookie store needed |
| Facebook missing email | Block login, redirect `/login?error` | Can't create User without email (NOT NULL constraint) |
| Twitter missing email | Placeholder `twitter:{id}@noemail.local` | Twitter API v2 does not expose email; placeholder avoids constraint violation |
| role on new User | `UserRole.USER` explicit in service | DB default is `ADMIN` — fail-open trap; must override in code |
| Token encryption | Deferred | Jasypt/AES column encryption is out-of-scope for D3; plaintext is a known gap |
| V2 migration | Default ADMIN→USER at DB level | Belt-and-suspenders; service override is the primary control |

## Security Notes

- **BR-001 (role assignment):** `CustomOAuth2UserService` always sets `role = UserRole.USER` on new User records. The `users.role` DB column default (`ADMIN`) is a documented fail-open that the service overrides. V2 migration also changes the DB default to `USER` as an additional guard.
- **BR-002 (token logging):** Access token value is never passed to any logger. Token is extracted and immediately written to `SocialAccount.accessToken` only.
- **BR-005 (token encryption):** Access and refresh tokens stored plaintext at D3. Encryption at rest must be addressed before production deployment.
- **Known gap — CSRF + REST API:** POST `/import-posts`, DELETE `/posts/{id}`, and other state-mutating REST endpoints now require a CSRF token. Swagger try-it-out returns 403 without token. A separate `SecurityFilterChain` (lower `@Order`, stateless, CSRF disabled) for API paths should be designed before D4 integration work.

## Deferred Items

- Token encryption at rest (Jasypt/AES)
- X-Forwarded-For trust policy for rate limiting / auth auditing
- Redis/JDBC session clustering (single-instance scope at D3)
- Dashboard controller at `/` — D4/D5 scope; success handler redirects there, currently 404
- Separate REST `SecurityFilterChain` to resolve CSRF + Swagger gap
- Admin provisioning mechanism (no path to `ADMIN` role via social login by design)

## Unresolved Questions Carried Forward

1. **CSRF + REST API:** Decide before D4 whether to add a second filter chain for API paths or disable CSRF selectively per path.
2. **Admin provisioning:** If admin features are planned in D4+, a provisioning mechanism must be designed — no in-app path exists to assign `ADMIN` role.
3. **Twitter offline.access + refresh token:** Confirm whether X returns a `refresh_token` with `offline.access` scope granted, and how Spring Security surfaces it for persistence.

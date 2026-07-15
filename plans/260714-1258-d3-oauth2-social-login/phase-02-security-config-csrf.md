# Phase 2 — SecurityConfig + CSRF

## Context links

- Spec: `plans/260714-1258-d3-oauth2-social-login/spec/oauth2-social-login/technical-spec.md` (FR-001, FR-002, BR-004, DEC-002, SM-001)
- Permissions: `plans/260714-1258-d3-oauth2-social-login/spec/system/permissions.md` (path authorization matrix, CSRF policy)
- Architecture: `plans/260714-1258-d3-oauth2-social-login/spec/system/architecture.md` (filter chain diagram)
- Research: `plans/reports/researcher-260714-1150-spring-oauth2-fb-twitter.md` §5

## Overview

| Field | Value |
|-------|-------|
| Priority | P0 |
| Status | completed 2026-07-14 |
| Tasks | D3-02 (SecurityConfig), D3-03 (CSRF meta tag / config) |
| Description | Create `SecurityConfig` — single `SecurityFilterChain` bean with `oauth2Login()`, CSRF enabled (default store + XOR handler), permitted-path list, success/failure handlers, logout config. |

## Requirements

- FR-001 — all paths except permitted list require authenticated session
- FR-002 — CSRF enabled for all state-mutating requests; Thymeleaf `th:action` forms auto-inject token
- FR-005 — `/login` publicly accessible
- FR-012 — Swagger paths permitted without authentication
- FR-013 — `/logout` invalidates session and redirects to `/login?logout`
- BR-004_CsrfProtection — CSRF validation enforced; GET requests exempt by default
- DEC-002_PostLoginRedirect — success handler sends to `/`; failure handler sends to `/login?error`
- SM-001_AuthenticationSessionLifecycle — session lifecycle state machine satisfied by filter chain DSL
- US004_ProtectedRouteEnforcement — unauthenticated GET `/posts` → 302 to `/login`

## Code files

### Existing to modify

- None

### New to create

- `src/main/java/com/sunasterisk/socialanalytics/config/SecurityConfig.java`

## Implementation steps

1. **Create `SecurityConfig`** in package `com.sunasterisk.socialanalytics.config`:
   - Annotate `@Configuration @EnableWebSecurity`
   - Single `@Bean SecurityFilterChain filterChain(HttpSecurity http, CustomOAuth2UserService oauth2UserService)`

2. **`authorizeHttpRequests` block** — permitted paths (no session required):
   ```
   /login, /login/**
   /oauth2/**, /login/oauth2/**
   /logout
   /swagger-ui/**, /swagger-ui.html
   /v3/api-docs/**
   /css/**, /js/**, /images/**, /webjars/**
   /error
   ```
   Everything else: `.anyRequest().authenticated()`

3. **`oauth2Login` block**:
   - `.loginPage("/login")` — custom login page (Phase 4)
   - `.userInfoEndpoint(ui -> ui.userService(oauth2UserService))` — wires CustomOAuth2UserService (Phase 3)
   - `.successHandler(...)` — lambda: `response.sendRedirect("/")` (DEC-002; 404 at `/` is acceptable in D3)
   - `.failureHandler(...)` — lambda: `response.sendRedirect("/login?error")`

4. **`logout` block**:
   - `.logoutSuccessUrl("/login?logout").permitAll()`

5. **`csrf` block** — use Spring Security 7 defaults (no override needed):
   - Default: `HttpSessionCsrfTokenRepository` (session-backed) — correct for server-rendered Thymeleaf
   - Default handler: `XorCsrfTokenRequestAttributeHandler` (BREACH protection)
   - No `CookieCsrfTokenRepository` needed (login page has no JS reading cookie)
   - Do NOT disable CSRF — POST `/import-posts` and DELETE `/posts/{id}` are protected (resolved decision #1)

6. **No `@Bean` for `AuthenticationSuccessHandler`/`AuthenticationFailureHandler`** — use inline lambdas to keep the class concise (KISS).

7. **Spring Security 7 DSL reminder** — `and()` removed; lambda DSL only. No `authorizeRequests()` (use `authorizeHttpRequests()`). `HttpSecurity.apply()` removed (use `.with()`). All already accounted for in the snippet above.

## Todo checklist

- [ ] Create `SecurityConfig.java` with `@Configuration @EnableWebSecurity`
- [ ] Implement `filterChain` bean with full permitted-path list
- [ ] Wire `oauth2Login()` with loginPage, userService, successHandler, failureHandler
- [ ] Wire `logout()` with `logoutSuccessUrl("/login?logout")`
- [ ] Enable CSRF with default repository and XOR handler (or verify defaults apply without explicit config)
- [ ] Confirm `CustomOAuth2UserService` parameter is injected (not yet created — Phase 3; compile will fail until Phase 3 is done)
- [ ] Verify `/swagger-ui.html` redirect path is covered (springdoc uses `/swagger-ui.html` → `/swagger-ui/index.html`)

## Success criteria

- SC-006 — GET `/login` returns HTTP 200 (no session required)
- SC-007 — GET `/posts` without session returns 302 redirect to `/login`
- SC-009 — GET `/swagger-ui/index.html` without session returns 200
- SC-010 — GET `/logout` with authenticated session → 302 to `/login?logout`
- SC-002 — POST to protected endpoint without CSRF token returns 403

## Risk assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Swagger try-it-out receives 403 on POST endpoints (CSRF not sent) | High | Low — known trade-off; D3 scope only | Document in README; resolution deferred to later iteration (separate API filter chain) |
| `CustomOAuth2UserService` not yet created when compiling Phase 2 | High (ordering issue) | Low | Phases 2 and 3 can be developed together; compile only after Phase 3 exists |
| Spring Security 7 lambda DSL breaking changes vs SS6 | Low | High | Research confirms lambda-only DSL; `and()` and `authorizeRequests()` are gone — verified |

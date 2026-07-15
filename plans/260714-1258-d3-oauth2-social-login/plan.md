---
work_type: feature
spec: docs/features/F004_Oauth2SocialLogin/
spec_lang: en
system_spec_draft: plans/260714-1258-d3-oauth2-social-login/spec/system/
status: completed
completed_date: 2026-07-14
branch: feature/social-login
---

# Day 3 — Spring Security + OAuth2 Social Login (Facebook & Twitter/X)

## Goal

Lock every application endpoint behind Spring Security; enable social login via Facebook (built-in `CommonOAuth2Provider`) and Twitter/X (manual provider, PKCE auto-on in SS7); persist `User` (role=USER) and `SocialAccount` (access token) on first and subsequent logins; ship a Thymeleaf login page.

## Spec references

- Spec: `plans/260714-1258-d3-oauth2-social-login/spec/oauth2-social-login/technical-spec.md`
- Architecture: `plans/260714-1258-d3-oauth2-social-login/spec/system/architecture.md`
- Permissions: `plans/260714-1258-d3-oauth2-social-login/spec/system/permissions.md`
- Screen: `plans/260714-1258-d3-oauth2-social-login/spec/oauth2-social-login/screens/SCR-login/spec.md`
- Scout: `plans/reports/scout-260714-1304-d3-codebase.md`
- Research: `plans/reports/researcher-260714-1150-spring-oauth2-fb-twitter.md`

## Phases

| # | Phase | Tasks | Status | Priority |
|---|-------|-------|--------|----------|
| 1 | Dependencies + configuration | D3-01, D3-04 | completed 2026-07-14 | P0 |
| 2 | SecurityConfig + CSRF | D3-02, D3-03 | completed 2026-07-14 | P0 |
| 3 | CustomOAuth2UserService + LoginController | D3-05, D3-07 | completed 2026-07-14 | P0 |
| 4 | Login page template (SCR-login) | D3-06 | completed 2026-07-14 | P0 |
| 5 | Security tests + fix existing slice tests | D3-08, D3-09 | completed 2026-07-14 | P0 |

## Key decisions (resolved)

1. CSRF: single filter chain, default `HttpSessionCsrfTokenRepository` + `XorCsrfTokenRequestAttributeHandler`; Thymeleaf forms auto-inject token; REST test callers use `csrf()` post-processor.
2. Role: always set `UserRole.USER` explicitly in service layer — never rely on DB default ADMIN (BR-001).
3. No Facebook email: throw `OAuth2AuthenticationException`, redirect to `/login?error=email_required`.
4. Account linking: find `User` by email first; add `SocialAccount` to that user; else create new User (role=USER).
5. Post-login redirect: success handler sends to `/` (no dashboard yet — 404 acceptable in D3 scope, DEC-002).
6. Twitter: manual provider, PKCE auto-enabled (SS7), nested `{"data":{...}}` unwrapped in `CustomOAuth2UserService` (DEC-001).
7. Token encryption: plaintext at rest in D3; deferred per BR-005.

## Stack

Spring Boot 4.1.0 / Spring Security 7.1 / Java 26 / Maven / Thymeleaf (new dep) / JUnit5 / `@MockitoBean`

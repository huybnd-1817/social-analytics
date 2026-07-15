# Phase 4 — Login Page Template (SCR-login)

## Context links

- Screen spec: `plans/260714-1258-d3-oauth2-social-login/spec/oauth2-social-login/screens/SCR-login/spec.md`
- Screens list: `plans/260714-1258-d3-oauth2-social-login/spec/oauth2-social-login/screens.md`
- Spec edge cases: `plans/260714-1258-d3-oauth2-social-login/spec/oauth2-social-login/edge-cases.md`
- Architecture: `plans/260714-1258-d3-oauth2-social-login/spec/system/architecture.md` (Thymeleaf integration)

## Overview

| Field | Value |
|-------|-------|
| Priority | P0 |
| Status | completed 2026-07-14 |
| Tasks | D3-06 (login page with provider buttons) |
| Description | Create `templates/login.html` — Thymeleaf template with branding (R1), conditional message band (R2), and two provider anchor links (R3). No forms — provider buttons are `<a>` tags. |

## Requirements

- US001_SocialLoginInitiation acceptance scenarios 1–4 — login page renders; two provider buttons; error/logout message bands
- SCR-login layout: R1 Branding (static), R2 Message Band (conditional on `?error` / `?logout`), R3 Provider Buttons (static)
- FR-005 — page publicly accessible
- CSRF: login page has NO `<form>` elements — provider buttons are `<a href="/oauth2/authorization/...">` tags, so CSRF injection is not needed here

## Code files

### Existing to modify

- `src/main/resources/templates/login/` — directory already exists (empty); template goes in `templates/login.html` at parent level (Thymeleaf maps `/login` → `templates/login.html`, not `templates/login/login.html`)

### New to create

- `src/main/resources/templates/login.html`

## Implementation steps

1. **Template skeleton** — standard Thymeleaf HTML5:
   ```html
   <!DOCTYPE html>
   <html xmlns:th="http://www.thymeleaf.org"
         xmlns:sec="http://www.thymeleaf.org/extras/springsecurity6">
   <head>
     <meta charset="UTF-8">
     <title>Social Analytics — Login</title>
   </head>
   <body>
   ```

2. **R1 Branding region** — static heading:
   ```html
   <header>
     <h1>Social Analytics Dashboard</h1>
   </header>
   ```

3. **R2 Message Band** — conditional `<div>` blocks:
   - Error state: `th:if="${error}"` → `role="alert"` + text "Login failed. Please try again."
   - Logout state: `th:if="${logout}"` → `role="status"` + text "You have been logged out."
   - Controlled by `LoginController` model attributes `error` (Boolean) and `logout` (Boolean)
   - Using `role="alert"` for error (screen reader accessibility per SCR-login §Accessibility)
   - Using `role="status"` for logout success (non-urgent confirmation)

4. **R3 Provider Buttons** — anchor tags (NOT `<form>` / `<button type="submit">`):
   ```html
   <main>
     <a href="/oauth2/authorization/facebook"
        aria-label="Continue with Facebook">
       Continue with Facebook
     </a>
     <a href="/oauth2/authorization/twitter"
        aria-label="Continue with Twitter / X">
       Continue with Twitter / X
     </a>
   </main>
   ```
   These are plain `<a>` GET links — Spring Security's `OAuth2AuthorizationRequestRedirectFilter` intercepts `/oauth2/authorization/{registrationId}` and initiates the flow. No CSRF token needed.

5. **Static styling** (minimal — no separate CSS file in D3):
   - Inline styles or a `<style>` block for centered card layout
   - Keep it functional; D4/D5 will add polished UI

6. **Thymeleaf `sec:` namespace** — imported via `xmlns:sec` but not used on the login page itself (login page is unauthenticated context). Included for consistency so the namespace is available when other templates are added.

7. **Template resolution** — confirm `application.properties` has:
   ```properties
   spring.thymeleaf.prefix=classpath:/templates/
   spring.thymeleaf.suffix=.html
   ```
   These already exist (noted in scout report). Thymeleaf will resolve controller return value `"login"` → `classpath:/templates/login.html`. The existing `templates/login/` directory is a subdirectory and does NOT conflict.

## Todo checklist

- [ ] Create `src/main/resources/templates/login.html`
- [ ] R1: Branding `<h1>` heading
- [ ] R2: Error message band with `th:if="${error}"` and `role="alert"`
- [ ] R2: Logout message band with `th:if="${logout}"` and `role="status"`
- [ ] R3: Facebook provider anchor `href="/oauth2/authorization/facebook"` with `aria-label`
- [ ] R3: Twitter provider anchor `href="/oauth2/authorization/twitter"` with `aria-label`
- [ ] Verify `templates/login/` subdirectory does not shadow `templates/login.html` (it should not — Thymeleaf resolves by exact suffix match on view name)
- [ ] Manual smoke test: start app, navigate to `/login`, confirm both buttons and message bands render correctly

## Success criteria

- SC-006 — GET `/login` with no session returns HTTP 200 with HTML body containing both provider anchor links
- US001 acceptance scenario 1 — unauthenticated visit to protected path → redirect to `/login`
- US001 acceptance scenario 2 — login page shows "Continue with Facebook" and "Continue with Twitter / X" buttons
- US001 acceptance scenario 3 — `/login?error` shows error message band
- US001 acceptance scenario 4 — `/login?logout` shows logout confirmation message band

## Risk assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| `templates/login/` subdirectory (already exists, empty) shadowing `templates/login.html` | Low | High — 404 on `/login` | Thymeleaf resolves `"login"` to `templates/login.html` (exact `{prefix}login{suffix}`), not to directory; confirmed by Thymeleaf resolution contract. Smoke test will catch it. |
| Thymeleaf `sec:` dialect not activating (BOM compat issue from Phase 1) | Medium | Low — login page does not use `sec:` dialect | Login page is safe; impact only if other templates use `sec:authentication` etc. in later phases |
| `spring.thymeleaf.cache=false` not set in dev profile | Low | Low — dev pain only | Already set in `application-dev.properties` (scout confirmed) |

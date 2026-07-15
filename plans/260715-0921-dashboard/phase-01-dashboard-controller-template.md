# Phase 01 — DashboardController + dashboard.html

**Status:** completed
**Priority:** high

## Context Links

- Spec: `plans/260715-0921-dashboard/spec/dashboard/technical-spec.md`
- Login template (style reference): `src/main/resources/templates/login.html`
- SecurityConfig: `src/main/java/com/sunasterisk/socialanalytics/config/SecurityConfig.java`

## Overview

Add `GET /` route mapping and a Thymeleaf template so authenticated users land on a dashboard instead of 404.

## Requirements

- R1: `DashboardController` maps `GET /` → returns `"dashboard"` view
- R2: Inject `name`, `email`, `provider` from `OAuth2AuthenticationToken` into model
- R3: `dashboard.html` shows name, email (N/A for Twitter null), provider badge, logout form
- R4: Logout form POSTs to `/logout`; Thymeleaf `th:action` auto-injects CSRF
- R5: Inline CSS consistent with `login.html`

## Files to Create

| File | Purpose |
|---|---|
| `src/main/java/com/sunasterisk/socialanalytics/controller/DashboardController.java` | @Controller GET / |
| `src/main/resources/templates/dashboard.html` | Thymeleaf dashboard template |

## Implementation Steps

1. Create `DashboardController`:
   - `@Controller`, `@GetMapping("/")`
   - Parameter: `@AuthenticationPrincipal OAuth2User principal`, `OAuth2AuthenticationToken authentication`
   - Extract: `name = principal.getAttribute("name")`, `email = principal.getAttribute("email")`, `provider = authentication.getAuthorizedClientRegistrationId()`
   - Add all three to `Model`, return `"dashboard"`

2. Create `dashboard.html`:
   - Same CSS base as `login.html` (font, background, card, box-shadow)
   - Header: "Social Analytics" title + "Welcome, {name}" subtitle
   - Info section: two-row table (Email / Provider)
   - Provider badge: "Facebook" or "X (Twitter)" based on `${provider}`
   - Logout: `<form th:action="@{/logout}" method="post">` + `<button type="submit">`

3. Compile check: `./mvnw compile -q`

## Todo

- [x] DashboardController.java
- [x] dashboard.html
- [x] mvnw compile -q (zero errors)

## Success Criteria

- GET / returns 200 with user name visible in HTML
- Logout button present (form + CSRF field auto-injected by Thymeleaf)
- Twitter email null → template shows "N/A" without error

## Security Considerations

- No change to SecurityConfig — `anyRequest().authenticated()` already protects `/`
- Logout uses CSRF (Spring Security 7 default)

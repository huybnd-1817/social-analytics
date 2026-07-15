---
status: draft
authored_by: takumi
created: 2026-07-14
---

# SCR-login — Screen Spec

**Screen**: SCR-login (TBD — SCR### allocated at promote)
**Feature**: oauth2-social-login (F### TBD)
**Type**: atomic
**Route**: `/login`
**Generated**: 2026-07-14

## Purpose

Unauthenticated visitors land on this screen to initiate social login via Facebook or Twitter/X; it is the sole entry point to the application and displays contextual messages for login errors and successful logout.

## Screen Layout

The login screen is a single centered card layout with no navigation bar or sidebar. It contains three regions: a branding area at the top, a provider button group in the center, and an optional message band that appears only when URL parameters (`?error` or `?logout`) are present.

### Layout Sketch

```
┌───────────────────────────────────────┐
│  R1: Branding (static)                │
│  [App name / logo]                    │
├───────────────────────────────────────┤
│  R2: Message Band (conditional)       │
│  [Error or logout confirmation text]  │
│  (dashed — hidden when no ?param)     │
├ - - - - - - - - - - - - - - - - - - -┤
│  R3: Provider Buttons (static)        │
│  [ Continue with Facebook ]           │
│  [ Continue with Twitter / X ]        │
└───────────────────────────────────────┘
```

### Layout Regions

| Region ID | Name | Position | Scrollable | Key Components | Responsive Behavior |
|-----------|------|----------|------------|----------------|---------------------|
| R1 | Branding | static, top of card | no | Application name heading, optional logo `<img>` | always visible; centered |
| R2 | Message Band | static, below branding | no | Thymeleaf `th:if` conditional `<div>` with alert styling | visible only when `?error` or `?logout` present in URL; hidden otherwise |
| R3 | Provider Buttons | static, center of card | no | Two `<a>` tags styled as buttons linking to `/oauth2/authorization/facebook` and `/oauth2/authorization/twitter` | always visible; stacked vertically; full-width on small screens |

## User Flow

### Happy Path

1. User arrives at `/login` (redirected from a protected path or navigated directly) — R1 branding visible, R3 buttons visible, R2 message band hidden.
2. User clicks "Continue with Facebook" (R3) — browser navigates to `/oauth2/authorization/facebook`; Spring Security initiates the OAuth2 redirect to Facebook.
3. _(Alternatively)_ User clicks "Continue with Twitter / X" (R3) — browser navigates to `/oauth2/authorization/twitter`; Spring Security initiates the PKCE OAuth2 redirect to Twitter/X.
4. After successful provider authorization, the browser is redirected back to `/login/oauth2/code/{registrationId}` (handled server-side by Spring Security); user is then forwarded to `/` — they do not see the login screen again.

### Branches

| Decision point | Condition | Outcome on this screen | Source |
|----------------|-----------|------------------------|--------|
| Page load | URL contains `?error` | R2 Message Band renders with error text: "Login failed. Please try again." | `TBD (draft)` |
| Page load | URL contains `?logout` | R2 Message Band renders with success text: "You have been logged out." | `TBD (draft)` |
| Page load | No URL parameter | R2 Message Band is hidden (`th:if` evaluates false) | `TBD (draft)` |

## UI States

| State | Trigger | Visual Behavior | User Action Available |
|-------|---------|----------------|-----------------------|
| default | Page load with no URL parameters | R1 + R3 visible; R2 hidden | Click either provider button |
| error | Page load with `?error` in URL | R2 visible with error message in alert styling | Click either provider button to retry |
| logout-confirmed | Page load with `?logout` in URL | R2 visible with logout confirmation in success styling | Click either provider button to log in again |
| redirecting | User clicks a provider button | Browser navigates away immediately; no loading spinner on this screen | None (navigation in progress) |

## Validation & Error Feedback

### A) Client-side

N/A — no client-side form validation detected. The login screen has no input fields; the only interactive elements are two anchor links.

### B) Server-side

N/A — no submit-style action handlers detected on this screen. Login errors surface as URL parameters (`?error`) set by `AuthenticationFailureHandler` on the server side; the screen reads and displays them passively.

## Accessibility

| Aspect | Status | Notes |
|--------|--------|-------|
| ARIA roles/labels | partial | Provider buttons should carry descriptive `aria-label` attributes (e.g., `aria-label="Continue with Facebook"`); message band should use `role="alert"` for error state |
| Keyboard navigation | supported | Both provider buttons are `<a>` elements — natively keyboard-focusable via Tab; Enter activates |
| Focus management | unmanaged | No modal or drawer; no explicit focus trap needed; default browser focus order is sufficient |
| Screen reader compatibility | unknown | Semantic landmarks (`<main>`, `<header>`) and button labels need verification before production |

[NO_A11Y_DETECTED] — full accessibility audit needed before production release; the above reflects design intent, not verified implementation.

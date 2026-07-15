---
status: draft
authored_by: takumi
created: 2026-07-15
---

# SCR-dashboard — Screen Spec

**Screen:** SCR-dashboard
**Type:** atomic
**Route:** GET /
**Generated:** 2026-07-15

## Purpose

Post-login landing page. Shows the authenticated user's identity and provides a logout action.

## Screen Layout

### Layout Sketch

```
┌─────────────────────────────────────┐
│          Social Analytics           │  ← app title
│         Welcome, {name}             │  ← subtitle
├─────────────────────────────────────┤
│  📧 Email    │  {email | N/A}       │
│  🔗 Provider │  Facebook / X        │
├─────────────────────────────────────┤
│        [ Logout ]                   │  ← form POST /logout + CSRF
└─────────────────────────────────────┘
```

### Layout Regions

| Region | Description |
|---|---|
| Header | App title + "Welcome, {name}" |
| Info table | Email row + Provider row |
| Logout | Form with submit button; Thymeleaf th:action injects CSRF |

## User Flow

### Happy Path

1. Spring Security confirms authenticated session.
2. `DashboardController` resolves name, email, provider from principal.
3. Template renders card with user info.
4. User clicks Logout → POST /logout → redirect /login?logout.

### Branches

| Condition | Behaviour | Source |
|---|---|---|
| Twitter login (email null) | email row shows "N/A" | TBD (draft) |
| Unauthenticated request | Spring Security redirects to /login before controller | TBD (draft) |

## UI States

| State | Trigger | Behaviour | Source |
|---|---|---|---|
| Loaded | Authenticated GET / | Render user card | TBD (draft) |
| Post-logout | /login?logout received | Handled by login.html, not this screen | TBD (draft) |

## Validation & Error Feedback

No user-input fields. Logout CSRF failure (403) is handled by Spring Security's default error page.

## Accessibility

- Logout button is a `<button type="submit">` inside a `<form>` — keyboard accessible.
- Provider badge uses text label (no icon-only).

---
status: implemented
authored_by: takumi
fcode: F005
created: 2026-07-15
---

# SCR002_Dashboard — Screen Spec

**Screen:** SCR002_Dashboard
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
│  Email     │  {email | N/A}         │
│  Provider  │  Facebook / X          │
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
| Twitter login (email null) | email row shows "N/A" | DashboardController.java |
| Unauthenticated request | Spring Security redirects to /login | SecurityConfig.java |

## UI States

| State | Trigger | Behaviour | Source |
|---|---|---|---|
| Loaded | Authenticated GET / | Render user card | DashboardController.java |
| Post-logout | /login?logout | Handled by login.html | LoginController.java |

## Validation & Error Feedback

No user-input fields. Logout CSRF failure (403) handled by Spring Security default error response.

## Accessibility

- Logout button is `<button type="submit">` inside `<form>` — keyboard accessible.
- Provider displayed as text label.

---
status: draft
authored_by: takumi
created: 2026-07-15
lang: en
---

# Dashboard — Technical Spec

## Overview

A Thymeleaf-rendered dashboard page served at `GET /`. Accessible only to authenticated users (Spring Security default). Displays the logged-in user's name, email (or n/a for Twitter), and OAuth2 provider, plus a logout button that POSTs to `/logout` and redirects to `/login?logout`.

## Polymorphic Behavior

None. Single screen, no role branching.

## Cross-Cutting Logic

### Requirements

- R1: `GET /` requires authentication — unauthenticated requests redirect to `/login` (handled by Spring Security `anyRequest().authenticated()`; no controller change needed).
- R2: Controller injects `name`, `email`, `provider` model attributes from the `OAuth2User` principal and `OAuth2AuthenticationToken`.
- R3: Logout is a `<form>` POST to `/logout` with hidden CSRF token (Thymeleaf `th:action` auto-injects).
- R4: Style must be consistent with `login.html` (same font stack, color palette, inline CSS).
- R5: Twitter users have no email — template shows "N/A" when `email` attribute is null.

## User Stories

### US001 — View dashboard after login

**Actor:** Authenticated user (any provider)
**Goal:** Land on a meaningful page after OAuth2 login instead of 404.

**Happy Path:**
1. OAuth2 login completes; success handler redirects to `/`.
2. `DashboardController` loads name, email, provider from `OAuth2AuthenticationToken`.
3. Template renders user card with name, email (or N/A), provider badge, logout button.

**Requirements fulfilled:** R1, R2, R3, R4, R5

### US002 — Logout

**Actor:** Authenticated user on dashboard
**Goal:** End the session and return to login page.

**Happy Path:**
1. User clicks Logout button.
2. Browser POSTs to `/logout` with CSRF token.
3. Spring Security invalidates session → redirects to `/login?logout`.
4. Login page shows "You have been logged out successfully."

**Requirements fulfilled:** R3

## Key Entities

- `OAuth2User` (Spring Security) — source of `name` and `email` attributes.
- `OAuth2AuthenticationToken` (Spring Security) — source of `authorizedClientRegistrationId` ("facebook" | "twitter").

## Artifact References

| Artifact | Codes Used |
|---|---|
| Feature List | Provisional F005 |
| Screens | TBD (draft) |
| Routes | TBD (draft) |
| Data Models | TBD (draft) |

## Assumptions

- `OAuth2User.getAttribute("name")` is always non-null (both providers supply it).
- `OAuth2User.getAttribute("email")` is null for Twitter — handled by R5.
- No new DB query needed; principal attributes are sufficient.

## Source Code References

No source code written yet — see `## User Stories` for planned controller and template.

## Unresolved Questions

_None._

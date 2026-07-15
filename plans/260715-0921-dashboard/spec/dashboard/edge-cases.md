---
status: draft
authored_by: takumi
created: 2026-07-15
lang: en
---

# Dashboard — Edge Cases

## EC-001: Twitter user — no email

**Condition:** `OAuth2User.getAttribute("email")` is null (Twitter doesn't expose email via OAuth2).
**Expected:** Template renders "N/A" in the email field. No NPE.

## EC-002: Unauthenticated access to GET /

**Condition:** User hits `/` without a valid session.
**Expected:** Spring Security intercepts → 302 redirect to `/login`. `DashboardController` is never invoked.

## EC-003: CSRF token missing on logout POST

**Condition:** Logout form submitted without the CSRF token.
**Expected:** Spring Security rejects with 403. Session not invalidated.

## EC-004: Principal name attribute null

**Condition:** Provider returns user with null `name` attribute (unlikely but possible).
**Expected:** Template shows empty string or "Unknown" — no crash.

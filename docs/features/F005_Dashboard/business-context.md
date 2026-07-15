---
status: implemented
authored_by: takumi
fcode: F005
created: 2026-07-15
lang: en
---

# Dashboard — Business Context

## Problem

After a successful Facebook/Twitter OAuth2 login, the app redirects to `/` — which returned 404 because no controller mapped that route.

## Solution

A minimal dashboard at `GET /` that confirms the user is logged in, shows their identity (name, email, provider), and provides a logout path.

## Stakeholders

- End users (Facebook / Twitter OAuth2 accounts)

## Success Criteria

- No 404 after login
- User sees their name and which provider they used
- Logout clears session and returns to `/login?logout`

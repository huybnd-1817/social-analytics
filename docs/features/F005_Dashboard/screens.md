---
status: implemented
authored_by: takumi
fcode: F005
created: 2026-07-15
lang: en
---

# Dashboard — Screen List

## Screen List

| Screen | Route | Type | Description |
|---|---|---|---|
| SCR002_Dashboard | GET / | atomic | Post-login landing page — user info card + logout |

## User Journey

1. OAuth2 login → success handler → GET /
2. User views dashboard (name, email, provider, logout)
3. Click Logout → POST /logout → GET /login?logout

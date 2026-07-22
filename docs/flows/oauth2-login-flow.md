---
slug: oauth2-login-flow
status: implemented
authored_by: rebuild-spec
created: 2026-07-21
lang: en
features: [F004]
---

# Process Flow: OAuth2 Social Login

## Overview

Full authorization_code + PKCE flow for Facebook and Twitter/X. Covers the happy path, error states, and session lifecycle.

## Actors

| Actor | Type | Description |
|-------|------|-------------|
| User | Human | Unauthenticated browser user |
| LoginController | System | Serves the /login Thymeleaf page |
| Spring Security | System | OAuth2 client filter chain |
| Facebook / Twitter API | External | Authorization server + user-info endpoint |
| CustomOAuth2UserService | System | Upserts User + SocialAccount records |
| UserRepository | System | Finds or creates User by email / provider_account_id |
| SocialAccountRepository | System | Upserts SocialAccount with latest access token |

## Happy Path (Facebook)

```
User
  │
  │  GET /login
  │
  ▼
LoginController → render login.html (OAuth2 buttons)
  │
  │  click "Login with Facebook"
  │
  ▼
Browser → GET /oauth2/authorization/facebook
  │
  ▼
Spring Security OAuth2LoginAuthFilter
  ├─ Generates state + PKCE code_verifier / code_challenge
  └─ Redirects browser → Facebook authorization endpoint
       with: client_id, redirect_uri, scope, state, code_challenge

Facebook Authorization Server
  │  User grants access
  │
  └─ Redirects → GET /login/oauth2/code/facebook?code=...&state=...

Spring Security OAuth2CallbackFilter
  ├─ Validates state
  ├─ Exchanges code for access_token (POST to Facebook token endpoint)
  ├─ Fetches user-info (GET Facebook graph API: name, email, picture)
  └─ Calls CustomOAuth2UserService.loadUser(userRequest)
       │
       ├─ Extract: name, email, avatar_url, provider_account_id
       ├─ UserRepository.findByEmail(email)
       │     ├─ found → update name, avatar_url
       │     └─ not found → create new User (role=USER)
       ├─ SocialAccountRepository.findByProviderAndProviderAccountId(...)
       │     ├─ found → update access_token, token_expires_at
       │     └─ not found → create new SocialAccount
       └─ Return DefaultOAuth2User with ROLE_USER authority

Spring Security
  ├─ Creates authenticated session
  └─ Redirects → / (dashboard)

User
  └─ Sees dashboard with name, email, provider badge
```

## Happy Path (Twitter/X)

Identical to Facebook except:
- Provider: twitter (manual registration — not in CommonOAuth2Provider)
- scope: `users.read tweet.read offline.access`
- user-info-uri: `https://api.twitter.com/2/users/me?user.fields=name,username`
- **Email is not returned** — CustomOAuth2UserService uses `provider_account_id` as the primary lookup key for Twitter users; email field remains null

## Error Paths

### OAuth2 Authorization Denied

```
User denies access at Facebook/Twitter
  │
  └─ Provider redirects → /login/oauth2/code/{provider}?error=access_denied
       │
       ▼
Spring Security → redirects → /login?error
       │
       ▼
LoginController renders login.html with error banner
```

### Missing Email (Facebook)

```
CustomOAuth2UserService.loadUser()
  │
  └─ Facebook response has no email attribute
       │
       └─ Throws OAuth2AuthenticationException("Email not provided by Facebook")
            │
            ▼
Spring Security → redirects → /login?error
```

### Logout Flow

```
User clicks Logout button (POST /logout with CSRF token)
  │
  ▼
Spring Security LogoutFilter
  ├─ Invalidates HttpSession
  ├─ Clears SecurityContext
  └─ Redirects → /login?logout

LoginController renders login.html with logout-success banner
```

## Session Lifecycle

```
Unauthenticated request to protected path
  └─ Spring Security → 302 → /login

Successful OAuth2 callback
  └─ Session created (JSESSIONID cookie set)

Subsequent requests (with JSESSIONID)
  └─ Spring Security restores authentication from session

POST /logout
  └─ Session invalidated; cookie cleared
```

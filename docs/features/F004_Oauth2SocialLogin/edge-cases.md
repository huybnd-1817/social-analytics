---
status: draft
authored_by: takumi
created: 2026-07-14
lang: en
---

# Edge Cases — OAuth2 Social Login

| Scenario | What Happens | User-Facing Message |
|----------|--------------|---------------------|
| User denies permission on Facebook or cancels the authorization dialog | Provider returns an error code to the callback URL; `AuthenticationFailureHandler` redirects to `/login?error` | "Login failed. Please try again." |
| Twitter/X nested user-info response fails to unwrap (malformed or missing `data` key — e.g., API contract change) | `CustomOAuth2UserService.loadUser()` throws `OAuth2AuthenticationException`; failure handler redirects to `/login?error` | "Login failed. Please try again." |
| OAuth2 state parameter mismatch (CSRF nonce tampered during redirect) | Spring Security rejects the callback; redirects to `/login?error` | "Login failed. Please try again." |
| Facebook `email` scope not granted (user opts out) — no email available to key the `User` record | Service cannot identify or create a user without email; throws `OAuth2AuthenticationException` | "Login failed. Please try again." (unresolved: fallback strategy not yet decided — see Unresolved Questions in technical-spec.md) |
| Placeholder `client-id` used in dev — user clicks provider button | Browser redirects to Facebook/Twitter with fake credentials; provider immediately rejects with `invalid_client`; failure handler redirects to `/login?error` | "Login failed. Please try again." |
| Database write fails during upsert (e.g., transient Postgres connectivity loss) | `DataAccessException` propagates; Spring Security wraps it in `OAuth2AuthenticationException`; failure handler redirects to `/login?error` | "Login failed. Please try again." |
| User with `(provider, providerAccountId)` already exists but token update fails mid-transaction | Transaction rolls back; no partial upsert; existing `SocialAccount` record is unchanged | "Login failed. Please try again." |
| Unauthenticated request to POST endpoint (e.g., `/import-posts`) without CSRF token | Spring Security rejects with HTTP 403 before reaching the controller | No redirect — API-level rejection (no user-facing HTML message; Swagger try-it-out will show a 403 response) |
| Valid session but CSRF token is stale or tampered on a Thymeleaf form submission | Spring Security returns HTTP 403 | "Forbidden — your session may have expired. Refresh the page and try again." (to be finalized in Thymeleaf error template) |
| Second login with the same provider account (returning user) | `SocialAccountRepository.findByProviderAndProviderAccountId()` returns existing row; `accessToken` is updated; no duplicate row inserted | None — transparent to user; redirected to dashboard as normal |
| User logs out then immediately clicks browser back button to a cached protected page | Browser may serve cached HTML; any subsequent API call or page reload without a session triggers 302 redirect to `/login` | None on cached view; subsequent request shows login page |

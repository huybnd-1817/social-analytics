# Phase 1 — Dependencies + Configuration

## Context links

- Spec: `plans/260714-1258-d3-oauth2-social-login/spec/oauth2-social-login/technical-spec.md`
- Architecture: `plans/260714-1258-d3-oauth2-social-login/spec/system/architecture.md`
- Research: `plans/reports/researcher-260714-1150-spring-oauth2-fb-twitter.md` §1, §2, §3, §7

## Overview

| Field | Value |
|-------|-------|
| Priority | P0 |
| Status | completed 2026-07-14 |
| Tasks | D3-01 (add deps), D3-04 (configure OAuth2 providers) |
| Description | Add 4 Maven dependencies to `pom.xml`; add OAuth2 provider registration + placeholder credentials to `application.properties`. No new Java classes. |

## Requirements

- FR-001 — security filter chain requires `spring-boot-starter-security` and `spring-boot-starter-oauth2-client`
- FR-009 — Twitter/X manual provider registration in `application.properties` with PKCE (auto in SS7)
- INT-001_FacebookOAuth2 — `CommonOAuth2Provider.FACEBOOK`, scope must include `email` explicitly
- INT-002_TwitterXOAuth2 — manual provider, `client-authentication-method: client-secret-post`, scopes `users.read tweet.read offline.access`

## Code files

### Existing to modify

- `pom.xml` — add 4 dependencies
- `src/main/resources/application.properties` — add OAuth2 registration blocks
- `src/main/resources/application-dev.properties` — no change needed; verify `logging.level.org.hibernate.orm.jdbc.bind` stays at DEBUG (not TRACE) per BR-002

### New to create

- None in this phase

## Implementation steps

1. **Add Maven dependencies** to `pom.xml` inside `<dependencies>` (all versions managed by Spring Boot 4.1 BOM — no `<version>` tags):
   1.1. `spring-boot-starter-security`
   1.2. `spring-boot-starter-oauth2-client`
   1.3. `spring-boot-starter-thymeleaf`
   1.4. `thymeleaf-extras-springsecurity6` (BOM-managed; verify with `mvn dependency:tree | grep thymeleaf-extras` after adding)
   1.5. `spring-security-test` with `<scope>test</scope>`

2. **Add Facebook registration** to `application.properties`:
   ```properties
   spring.security.oauth2.client.registration.facebook.client-id=${FACEBOOK_CLIENT_ID:placeholder-fb}
   spring.security.oauth2.client.registration.facebook.client-secret=${FACEBOOK_CLIENT_SECRET:placeholder-fb-secret}
   spring.security.oauth2.client.registration.facebook.scope=public_profile,email
   ```
   CommonOAuth2Provider.FACEBOOK supplies auth-uri, token-uri, user-info-uri, user-name-attribute automatically.

3. **Add Twitter/X registration** to `application.properties`:
   ```properties
   spring.security.oauth2.client.registration.twitter.client-id=${TWITTER_CLIENT_ID:placeholder-tw}
   spring.security.oauth2.client.registration.twitter.client-secret=${TWITTER_CLIENT_SECRET:placeholder-tw-secret}
   spring.security.oauth2.client.registration.twitter.authorization-grant-type=authorization_code
   spring.security.oauth2.client.registration.twitter.client-authentication-method=client-secret-post
   spring.security.oauth2.client.registration.twitter.scope=users.read,tweet.read,offline.access
   spring.security.oauth2.client.registration.twitter.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}
   spring.security.oauth2.client.registration.twitter.provider=twitter
   spring.security.oauth2.client.provider.twitter.authorization-uri=https://x.com/i/oauth2/authorize
   spring.security.oauth2.client.provider.twitter.token-uri=https://api.twitter.com/2/oauth2/token
   spring.security.oauth2.client.provider.twitter.user-info-uri=https://api.twitter.com/2/users/me
   spring.security.oauth2.client.provider.twitter.user-info-authentication-method=header
   spring.security.oauth2.client.provider.twitter.user-name-attribute=id
   ```
   PKCE is auto-enabled in Spring Security 7 for all authorization_code flows — no extra property.

4. **Verify startup** after Phase 2 is complete: app should start without `BeanCreationException`; login page should render at `/login`; provider buttons should redirect to providers (which will reject the placeholder client-ids — that is expected behavior per §7 of research report).

## Todo checklist

- [ ] Add `spring-boot-starter-security` to `pom.xml`
- [ ] Add `spring-boot-starter-oauth2-client` to `pom.xml`
- [ ] Add `spring-boot-starter-thymeleaf` to `pom.xml`
- [ ] Add `thymeleaf-extras-springsecurity6` to `pom.xml` (no version — BOM)
- [ ] Add `spring-security-test` (test scope) to `pom.xml`
- [ ] Add Facebook OAuth2 registration properties to `application.properties`
- [ ] Add Twitter/X OAuth2 registration + provider properties to `application.properties`
- [ ] Run `mvn dependency:tree | grep thymeleaf-extras` to verify BOM resolves a compatible version
- [ ] Confirm `logging.level.org.hibernate.orm.jdbc.bind=DEBUG` (not TRACE) stays in `application-dev.properties`

## Success criteria

- `mvn compile` passes without errors
- `mvn dependency:tree` shows `thymeleaf-extras-springsecurity6` resolved
- Application starts (after Phase 2 exists) without `BeanCreationException`; `ClientRegistrationRepository` bean created automatically from properties

## Risk assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| `thymeleaf-extras-springsecurity6` BOM compatibility with SS7 (repo archived Apr 2026) | Medium | High — dialect may not auto-activate | Run `mvn dependency:tree` immediately; if BOM does not resolve it, add explicit version `3.1.5.RELEASE` as fallback |
| Twitter `client-authentication-method: client-secret-post` rejected by X token endpoint | Low-Medium | Medium — Twitter login fails at runtime | Verified in research report as correct for confidential server-side clients; runtime test with real credentials needed |
| Empty placeholder values causing startup crash | Low | High | Always use `${VAR:placeholder-value}` form with non-empty default; empty strings fail Spring Boot non-empty validation |

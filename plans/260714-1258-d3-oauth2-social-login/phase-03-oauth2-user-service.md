# Phase 3 — CustomOAuth2UserService + Entities + LoginController

## Context links

- Spec: `plans/260714-1258-d3-oauth2-social-login/spec/oauth2-social-login/technical-spec.md` (FR-004, FR-006..FR-011, BR-001..BR-003, DEC-001, DISC-001, DISC-002)
- Architecture: `plans/260714-1258-d3-oauth2-social-login/spec/system/architecture.md` (OAuth2 flow diagram)
- Research: `plans/reports/researcher-260714-1150-spring-oauth2-fb-twitter.md` §4
- Scout: `plans/reports/scout-260714-1304-d3-codebase.md` (entities + repos confirmed present)

## Overview

| Field | Value |
|-------|-------|
| Priority | P0 |
| Status | completed 2026-07-14 |
| Tasks | D3-05 (CustomOAuth2UserService), D3-07 (post-login redirect via success handler in SecurityConfig) |
| Description | Implement `CustomOAuth2UserService` extending `DefaultOAuth2UserService`; override `loadUser()` to dispatch by provider, unwrap Twitter nested response, upsert User+SocialAccount; add `/login` MVC controller; wire Thymeleaf model attributes for error/logout messages. |
| Post-review fixes | C-1: User.java @Builder.Default ADMIN→USER + V2 migration; H-1: null providerUserId throws OAuth2AuthenticationException; M-1: dead email!=null branch removed; twitter_missingId_throwsException test added |

## Requirements

- FR-004 / BR-001_RoleAssignmentOnUpsert — new User created with `role = UserRole.USER` (never rely on DB default ADMIN)
- FR-003 / BR-002_AccessTokenNeverLogged — access token value MUST NOT appear in any log statement
- BR-003_SocialAccountUpsert — keyed on `(provider, providerAccountId)` unique constraint; update existing row on re-login
- BR-005_TokenEncryptionDeferred — store token plaintext in D3; note as known gap
- DEC-001_TwitterUserInfoUnwrap — detect `registrationId == "twitter"`, extract `attributes["data"]` map, return `DefaultOAuth2User` with flattened data map + `"id"` as name attribute
- FR-006/FR-007/FR-008 — Facebook flat response extraction, User persist with role=USER, SocialAccount upsert
- FR-009/FR-010/FR-011 — Twitter manual provider, nested unwrap, DefaultOAuth2User with data map
- US002_FacebookOAuth2Login — account-linking: find User by email first; if found, add SocialAccount; else create new User
- Edge case: Facebook email absent → throw `OAuth2AuthenticationException` (redirect to `/login?error=email_required` via failure handler)

## Code files

### Existing to modify

- None (entities and repositories are confirmed present by scout report)

### New to create

- `src/main/java/com/sunasterisk/socialanalytics/security/CustomOAuth2UserService.java`
- `src/main/java/com/sunasterisk/socialanalytics/controller/LoginController.java`

## Implementation steps

### CustomOAuth2UserService

1. **Class skeleton** in package `com.sunasterisk.socialanalytics.security`:
   - `@Service` + `@Transactional`
   - Extends `DefaultOAuth2UserService`
   - Constructor-inject `UserRepository` and `SocialAccountRepository`

2. **`loadUser(OAuth2UserRequest userRequest)` override**:
   2.1. Call `super.loadUser(userRequest)` to get raw `OAuth2User` (Spring fetches user-info endpoint)
   2.2. Extract `registrationId` from `userRequest.getClientRegistration().getRegistrationId()`
   2.3. Extract `accessToken` from `userRequest.getAccessToken().getTokenValue()` — store in local var; NEVER log it
   2.4. **Twitter branch** (`"twitter".equals(registrationId)`):
        - Cast `oAuth2User.getAttributes().get("data")` to `Map<String, Object>` (suppress unchecked warning)
        - Extract `providerUserId = (String) data.get("id")` and `name = (String) data.get("name")`
        - Twitter does not return email — pass `null` for email
        - Call `upsertUser(SocialProvider.TWITTER, providerUserId, name, null, accessToken, userRequest)`
        - Return `new DefaultOAuth2User(oAuth2User.getAuthorities(), data, "id")`
   2.5. **Facebook branch** (else):
        - `providerUserId = (String) oAuth2User.getAttribute("id")`
        - `name = (String) oAuth2User.getAttribute("name")`
        - `email = (String) oAuth2User.getAttribute("email")`
        - If `email == null` or blank: throw `new OAuth2AuthenticationException("email_required")` — triggers failure handler → `/login?error`
        - Call `upsertUser(SocialProvider.FACEBOOK, providerUserId, name, email, accessToken, userRequest)`
        - Return the original `oAuth2User`

3. **`upsertUser` private method**:
   ```
   // Account-linking: find by email first (resolved decision #4)
   User user = (email != null)
       ? userRepository.findByEmail(email).orElseGet(() -> createNewUser(name, email))
       : createNewUser(name, email);  // Twitter — no email key
   
   // SocialAccount upsert (BR-003)
   SocialAccount sa = socialAccountRepository
       .findByProviderAndProviderAccountId(provider, providerUserId)
       .orElseGet(SocialAccount::new);
   sa.setUser(user);
   sa.setProvider(provider);
   sa.setProviderAccountId(providerUserId);
   sa.setAccessToken(accessToken);   // plaintext — BR-005 deferred
   // refreshToken / tokenExpiresAt: null in D3 (Twitter offline.access TBD at runtime)
   socialAccountRepository.save(sa);
   ```

4. **`createNewUser` private helper**:
   ```
   User u = new User();
   u.setName(name);
   u.setEmail(email);   // may be null for Twitter
   u.setRole(UserRole.USER);  // BR-001: EXPLICIT — never rely on DB default ADMIN
   return userRepository.save(u);
   ```

5. **BR-002 compliance**: no `log.debug/info/warn/error` calls that include `accessToken` or any token value anywhere in the class. Log only `registrationId` and `providerUserId` (safe identifiers).

### LoginController

6. **Create `LoginController`** in `com.sunasterisk.socialanalytics.controller`:
   - `@Controller` (not `@RestController` — returns view name)
   - `@GetMapping("/login")` method
   - Method signature: `String login(@RequestParam(required = false) String error, @RequestParam(required = false) String logout, Model model)`
   - Add `model.addAttribute("error", error != null)` and `model.addAttribute("logout", logout != null)`
   - Return `"login"` (resolves to `templates/login.html` via Thymeleaf)
   - Spring Security's `loginPage("/login")` intercepts the POST OAuth2 redirect; this controller only handles the GET display

## Todo checklist

- [ ] Create `security/` package under `com.sunasterisk.socialanalytics`
- [ ] Create `CustomOAuth2UserService.java` extending `DefaultOAuth2UserService`
- [ ] Implement Twitter branch: unwrap `attributes["data"]`, return flattened `DefaultOAuth2User`
- [ ] Implement Facebook branch: extract email; throw `OAuth2AuthenticationException` if absent
- [ ] Implement `upsertUser`: account-linking by email, SocialAccount upsert keyed on `(provider, providerAccountId)`
- [ ] Implement `createNewUser`: set `role = UserRole.USER` explicitly (BR-001)
- [ ] Verify no access token value appears in any log statement (BR-002)
- [ ] Add `@Transactional` to `loadUser()` to make upsert atomic (rollback on DB failure → failure handler)
- [ ] Create `LoginController.java` with GET `/login` mapping
- [ ] Wire `error` and `logout` model attributes for Thymeleaf template

## Success criteria

- SC-001 — unauthenticated GET `/posts` → 302 to `/login`
- SC-003 — after mock OAuth2 login, `users` table row has `role = 'USER'`
- SC-004 — no access token value in application log output
- SC-005 — second login with same provider account updates existing `social_accounts` row (no duplicate)
- SC-008 — Twitter mock login produces `SocialAccount` with `provider=TWITTER`, no `ClassCastException`
- Edge case: Facebook login without email → `OAuth2AuthenticationException` thrown → failure handler → `/login?error`

## Risk assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| `ClassCastException` on Twitter `data` map cast | Medium | Medium — Twitter login broken | Wrap in try-catch; rethrow as `OAuth2AuthenticationException` to trigger failure handler |
| Twitter account has no email → `user_id` null linkage in DB | Low | Low — Twitter never returns email; link only by `(provider, providerAccountId)` | Pass `null` email for Twitter; `User.email` column is `NOT NULL` — create User with generated placeholder or leave email null if column allows; revisit nullable constraint if needed |
| `@Transactional` on `loadUser()` — Spring Security calls this in filter chain (not in a servlet transaction) | Low | Medium — transaction may not roll back cleanly | Use `@Transactional(propagation = REQUIRES_NEW)` if issues arise; default REQUIRED is fine for initial implementation |
| `User.email` NOT NULL constraint breaks Twitter user creation (no email from Twitter) | Medium | High | Twitter users: either skip `User` creation (link only `SocialAccount`) or use a provider-scoped placeholder email like `"twitter:{providerUserId}@noemail.local"`. Revisit if FK constraint is hit. |

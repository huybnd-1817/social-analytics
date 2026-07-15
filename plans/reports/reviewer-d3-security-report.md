# Code Review — Day 3 Spring Security + OAuth2

**Date:** 2026-07-14
**Branch:** feature/social-login
**Reviewer:** reviewer agent

---

## Scope

| File | LOC | Notes |
|---|---|---|
| `config/SecurityConfig.java` | 46 | Single filter chain, CSRF comment |
| `security/CustomOAuth2UserService.java` | 118 | Core OAuth2 user provisioning |
| `controller/LoginController.java` | 19 | Simple GET handler |
| `templates/login.html` | 138 | Thymeleaf, anchor-based OAuth2 links |
| `application.properties` (lines 77–100) | 24 | OAuth2 provider config |
| `config/SecurityConfigTest.java` | 68 | MockMvc slice tests |
| `security/CustomOAuth2UserServiceTest.java` | 269 | Unit tests via subclass |
| `controller/PostControllerTest.java` | 97 | Integration slice |

---

## Overall Assessment

The implementation is solid for a Day 3 delivery. All named business rules (BR-001 through BR-005) and resolved decisions are correctly honoured in code. The test suite is genuinely meaningful — no trivial stubs, real ArgumentCaptor assertions, correct error-code checks. Three issues need addressing before this ships: one **critical** (ADMIN leak via `@Builder` on `new User()`), two **high** (null providerUserId passed to DB, Swagger endpoints unauthenticated in prod), plus several medium/low items.

---

## Critical Issues

### C-1: `@Builder.Default = ADMIN` fires when `new User()` + setters path skips builder — BR-001 gap still live

**File:** `entity/User.java:33`, `security/CustomOAuth2UserService.java:110–116`

`User.java` uses `@Builder` with `@Builder.Default private UserRole role = UserRole.ADMIN`. `@Builder.Default` has no effect on the no-args constructor path. `createNewUser()` uses `new User()` then `setRole(UserRole.USER)`, so the explicit set on line 115 is what actually saves USER — the `@Builder.Default = ADMIN` is only dangerous if someone later calls `User.builder().build()` without setting role, which produces an ADMIN.

**The current `createNewUser` path is safe**, but the `@Builder.Default = ADMIN` is a loaded gun pointed at every future call site that uses the builder pattern without setting role. Any future code that does `User.builder().name("X").email("Y").build()` silently creates an ADMIN.

**Risk:** High — wrong default in the builder is a privilege escalation waiting to happen. The DB default (`DEFAULT 'ADMIN'` in V1 migration) compounds this: both the builder and Postgres default to ADMIN.

**Fix:** Change `@Builder.Default` to `UserRole.USER`, or remove `@Builder.Default` and set a `UserRole.USER` constant in the no-args constructor. The DB migration default should also be corrected:
```sql
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'USER';
```
Until fixed, any code path that creates a User via the builder without explicitly setting role silently grants admin.

---

## High Priority

### H-1: `providerUserId` is never null-guarded in the Twitter or Facebook path

**File:** `CustomOAuth2UserService.java:63–76`

Twitter: `providerUserId = (String) data.get("id")` — if the `data` map exists but lacks the `"id"` key, `providerUserId` is null. It is immediately concatenated into `placeholderEmail` at line 67 (`"twitter:null@noemail.local"`) and then passed to `findByProviderAndProviderAccountId(provider, null)`. With a non-null DB constraint on `provider_account_id`, this throws a `DataIntegrityViolationException` — an unchecked exception that is **not** an `OAuth2AuthenticationException`. Spring Security does not handle it cleanly; the user sees a 500 error page.

Facebook: same issue — `providerUserId = oAuth2User.getAttribute("id")` returns null if Facebook omits the field. `SocialAccount.providerAccountId` is `NOT NULL` in the schema.

**Fix:** After extracting `providerUserId`, validate it:
```java
if (providerUserId == null || providerUserId.isBlank()) {
    throw new OAuth2AuthenticationException("twitter_missing_id"); // or facebook_missing_id
}
```

### H-2: Swagger / OpenAPI endpoints are `permitAll()` — production exposure

**File:** `SecurityConfig.java:23–24`

`/swagger-ui/**` and `/v3/api-docs/**` are unauthenticated. This exposes the full API surface and schema to unauthenticated users in production. Acceptable in dev, not in a production deploy.

**Recommendation:** Guard behind a profile check or a separate admin-only rule:
```java
.requestMatchers("/swagger-ui/**", "/v3/api-docs/**")
    .hasRole("ADMIN")
```
Or restrict to a dev profile only. At minimum, document this as a known deployment decision, not an oversight.

---

## Medium Priority

### M-1: `upsertUser` dead branch — `email != null` guard is always true for Facebook, never reachable for Twitter

**File:** `CustomOAuth2UserService.java:94–96`

```java
User user = (email != null)
    ? userRepository.findByEmail(email).orElseGet(() -> createNewUser(name, email))
    : createNewUser(name, null);
```

For Facebook: `email` is already null-checked before `upsertUser` is called (thrown at line 81–83). The `else` branch (`createNewUser(name, null)`) is dead code in the Facebook path.
For Twitter: `placeholderEmail` is always non-null (string literal + concat), so the `else` branch is also dead.

The `null` email branch would call `createNewUser(name, null)` which would try to persist a `User` with `email = null`, violating the `NOT NULL` constraint. This dead branch is a silent time-bomb if a third provider is added later without properly wiring email.

**Fix:** Remove the ternary guard; both call sites guarantee a non-null email before reaching `upsertUser`. Or add an explicit assertion: `Objects.requireNonNull(email, "email must not be null before upsertUser")`.

### M-2: Twitter `name` is never null-guarded before placeholder construction

**File:** `CustomOAuth2UserService.java:64`

`name = (String) data.get("name")` can be null if Twitter v2 omits the field (e.g., empty profile). It is passed directly to `upsertUser` → `createNewUser`, where line 112 does handle it (`name != null ? name : "Unknown"`). So the DB won't break, but it is worth making explicit in the Twitter path rather than relying on downstream fallback.

### M-3: `handleFacebookUser` returns the raw `oAuth2User` — attributes may contain PII in session

**File:** `CustomOAuth2UserService.java:88`

`return oAuth2User;` returns the full Facebook attribute map (potentially including `picture`, `birthday`, and any other requested scopes) directly as the security principal. These attributes are stored in the `SecurityContext` and may end up serialized to the session, depending on session serialization config. The Twitter path correctly wraps and narrows to `new DefaultOAuth2User(authorities, data, "id")`.

**Consistency fix:** Wrap the Facebook return similarly, exposing only what downstream code actually needs:
```java
return new DefaultOAuth2User(oAuth2User.getAuthorities(),
    Map.of("id", providerUserId, "name", name, "email", email), "id");
```

### M-4: `SecurityConfigTest` does not test the redirect target for unauthenticated requests

**File:** `SecurityConfigTest.java:55–60`

`protectedResource_unauthenticated_redirectsToLogin` asserts `is3xxRedirection()` and `redirectedUrl("/login")` — correct. But it doesn't cover the full redirect URL that Spring Security produces, which is typically `/login` (no port, no HTTPS scheme prefix). This passes in MockMvc but may mask misconfigured `defaultRedirectStrategy` if the port-stripping behaviour changes. Low-risk but worth noting.

### M-5: No test covers the case where `twitter.data.id` is null

**File:** `CustomOAuth2UserServiceTest.java`

There is a test for missing top-level `"data"` key (`twitter_missing_data`), but no test for `data` present but `id` absent — which hits the null-concat path described in H-1. Add:
```java
@Test
void twitter_missingIdInData_throwsOAuth2AuthenticationException() { ... }
```

---

## Low Priority

### L-1: `client-authentication-method=client-secret-post` for Twitter may conflict with PKCE

**File:** `application.properties:92`

Twitter v2 OAuth2 with PKCE (which SS7 auto-enables) typically expects `client-secret-basic` or `none` as the authentication method, not `client-secret-post`, depending on the app type (confidential vs. public). If the Twitter app is registered as a confidential client, `client-secret-post` is correct; if public, it should be `none`. This is a runtime misconfiguration risk, not a compile-time one. Verify against the Twitter developer portal app settings.

### L-2: `logout` is `permitAll()` — no test for unauthenticated logout

**File:** `SecurityConfig.java:36–39`

`logout.permitAll()` in SS7 means unauthenticated POST to `/logout` is accepted and redirected to `/login?logout`. There is a test for authenticated logout with CSRF (`logout_withCsrf_redirectsToLoginWithLogoutParam`) but no test for unauthenticated logout. Not a security risk (CSRF still required), but a coverage gap.

### L-3: `UserRole` enum and `SocialProvider` enum not shown — verify no stale values

The `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` on both `User.role` and `SocialAccount.provider` means Hibernate uses the Postgres ENUM type. If a new enum constant is added to `UserRole` or `SocialProvider` in Java without a corresponding migration to add it to the Postgres type, Flyway will not catch it and the JPA insert will fail at runtime. This is an architectural note for the team, not a bug in the current code.

---

## Edge Cases Found

1. **Twitter user with no `username` field:** The Twitter v2 `/2/users/me` endpoint returns `id` and `name` by default but `username` is a separate field. The `data` map in `handleTwitterUser` only reads `id` and `name` — no reference to `username`. The returned `DefaultOAuth2User` uses `"id"` as the name key, which is correct. No issue.

2. **Email collision across providers:** If a Facebook user and a Twitter user share the same placeholder email (impossible by construction since Twitter emails are `twitter:ID@noemail.local`), but if a real Facebook user has an email that matches a placeholder, account linking would silently merge two unrelated identities. The Twitter placeholder domain (`noemail.local`) is non-routable and unlikely to appear as a real Facebook email. Acceptable for D3 scope.

3. **`SocialAccount::new` in `orElseGet`:** When `findByProviderAndProviderAccountId` returns empty, a new `SocialAccount` is constructed via `SocialAccount::new`. The `SocialAccount` entity has `@Builder` and `@NoArgsConstructor` — `new SocialAccount()` leaves `createdAt` null until `@CreatedDate` fills it on persist. Correct by design with `@EntityListeners(AuditingEntityListener.class)`.

4. **Concurrent first-time logins for the same email:** `findByEmail(...).orElseGet(() -> createNewUser(...))` is not atomic. Two concurrent requests for the same email will both see empty and both call `createNewUser`, hitting the `UNIQUE` constraint on `users.email`. This throws a `DataIntegrityViolationException` on the second insert — not an `OAuth2AuthenticationException`, so the user sees a 500. Mitigate with a `@Unique` retry or a `UPSERT` query. Acceptable to defer post-D3, but should be documented as a known race.

---

## Positive Observations

- BR-001 is correctly enforced in `createNewUser` with an explicit `setRole(UserRole.USER)` and a comment.
- BR-002 (no token logging) is clean — the token is used locally but never appears in any log statement.
- DEC-001 (Twitter `data` unwrapping) is handled correctly, including the `ClassCastException` guard.
- Resolved #3 (Facebook missing email) throws the correct typed `OAuth2AuthenticationException` with an inspectable error code — not a generic exception.
- Resolved #4 (email-first account linking + SocialAccount upsert) is implemented correctly and tested with `ArgumentCaptor`.
- The `fetchUserInfo()` protected-method seam for testability is the right pattern — avoids Mockito spy fragility on Spring-managed beans.
- Test assertions use `extracting(ex -> ex.getError().getErrorCode())` to verify the specific OAuth2 error code, not just the exception type — high signal.
- CSRF comment in `SecurityConfig` is accurate and explains the implicit Spring Security 7 behaviour clearly.
- `application.properties` uses `${ENV_VAR:placeholder-*}` correctly — no hardcoded secrets, app starts without credentials.

---

## Recommended Actions (Priority Order)

1. **[Critical]** Change `User.java` `@Builder.Default` to `UserRole.USER` and update the DB default in a new migration `V2__fix_user_role_default.sql` — eliminates the ADMIN privilege escalation trap.
2. **[High]** Add null-guard on `providerUserId` in both `handleTwitterUser` and `handleFacebookUser` before use — prevents `DataIntegrityViolationException` on malformed provider responses.
3. **[High]** Add a test case `twitter_missingIdInData_throwsOAuth2AuthenticationException` (M-5 / H-1 complement).
4. **[High]** Document Swagger `permitAll` as a deliberate decision or restrict to `ADMIN` role / dev profile.
5. **[Medium]** Remove the dead `email == null` branch in `upsertUser` or replace with `Objects.requireNonNull`.
6. **[Medium]** Narrow the Facebook return value to a `DefaultOAuth2User` with a minimal attribute set (M-3).
7. **[Low]** Verify Twitter `client-authentication-method` against actual app registration type in Twitter developer portal.
8. **[Low]** Document the concurrent-first-login race (edge case 4) in a follow-up ticket.

---

## Metrics

- Type Coverage: N/A (Java, not TypeScript)
- Meaningful test assertions: high — ArgumentCaptor, error-code extraction, never().save() guards
- Critical BR compliance: BR-001 ✓ (runtime path safe, builder default dangerous), BR-002 ✓, BR-005 acknowledged
- Linting issues: 0 visible (dead branch in `upsertUser` is the only smell)

---

## Unresolved Questions

- Is the Twitter developer app registered as **confidential** or **public**? Determines whether `client-secret-post` or `client-authentication-method=none` is correct for PKCE.
- Is Swagger intentionally public (dev-only deployment), or should it be access-controlled in production?

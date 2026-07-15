# Code Review — DashboardController (feature/social-login)

**Date:** 2026-07-15
**Branch:** feature/social-login
**Reviewer:** reviewer agent

---

## Scope

| File | LOC | Role |
|---|---|---|
| `DashboardController.java` | 26 | Controller |
| `dashboard.html` | 132 | Thymeleaf template |
| `DashboardControllerTest.java` | 83 | Unit tests |

---

## Overall Assessment

Implementation is clean and minimal. The happy path is correct and the Twitter null-email path is handled safely in the template. One **Important** issue (NPE on unauthenticated `principal`), one **Important** gap (no `provider` assertion in tests), one **Minor** note on unused annotation, and one **Minor** note on hardcoded provider logic in the template.

---

## Critical Issues

None.

---

## Important Issues

### I-1: NPE when `principal` is null (unauthenticated request reaches controller)

**File:** `DashboardController.java` lines 19–23

Spring Security redirects unauthenticated requests before the controller runs — but only when the filter chain is correctly wired. The test `dashboard_unauthenticated_redirectsToLogin` verifies this via `SecurityConfig`. If for any reason (e.g. a future misconfiguration, or a test context that doesn't load `SecurityConfig`) the filter chain doesn't fire, `principal` will be `null` and line 19 throws a `NullPointerException` with no error message to the caller.

**Fix:** add a null guard or use `@PreAuthorize` as a belt-and-suspenders backstop:

```java
if (principal == null) {
    return "redirect:/login";
}
```

This is cheap insurance and makes the method self-documenting about its intent.

### I-2: Tests do not assert the `provider` model attribute

**File:** `DashboardControllerTest.java`

All four tests verify `name` and `email` but none asserts `model().attribute("provider", ...)`. The `provider` attribute is rendered in the template (badge class, display text) and is extracted from `authentication.getAuthorizedClientRegistrationId()`. A regression on that line (e.g. using the wrong method) would not be caught.

`oauth2Login()` uses `"test"` as the default `registrationId`. The tests should either assert on `"test"` or set a specific value:

```java
.with(oauth2Login()
    .clientRegistration(/* custom reg with id "facebook" */)
    .attributes(attrs -> { ... }))
// then:
.andExpect(model().attribute("provider", "facebook"));
```

This is the only spec criterion (R2) not verified by any test.

---

## Minor Issues

### M-1: `@RequiredArgsConstructor` unused — no injected dependencies

**File:** `DashboardController.java` line 3 / line 12

`@RequiredArgsConstructor` generates a constructor for `final` fields. `DashboardController` has none. The annotation is harmless but misleading — a reader expects to find dependencies. Remove it (YAGNI).

### M-2: Provider badge logic is hardcoded to only two providers

**File:** `dashboard.html` lines 117–119

```html
th:classappend="${provider == 'facebook'} ? 'badge-facebook' : 'badge-twitter'"
th:text="${provider == 'facebook'} ? 'Facebook' : 'X (Twitter)'"
```

Any third provider added later (e.g. Google) silently renders the Twitter badge and label "X (Twitter)". This is acceptable for the current scope, but note it as a landmine if the provider list ever grows. At minimum, a comment flagging the assumption would prevent a future silent misrender.

---

## Security Findings

**XSS (Thymeleaf output):** All dynamic values (`name`, `email`, `provider`) are output via `th:text`, which HTML-escapes by default. No `th:utext` or unescaped interpolation is used. XSS risk: none.

**CSRF on logout:** `th:action="@{/logout}"` correctly triggers Thymeleaf's CSRF token injection. Spring Security's default `HttpSessionCsrfTokenRepository` + `XorCsrfTokenRequestAttributeHandler` is left intact (not disabled). CSRF protection is correct.

**Auth enforcement (R1):** `SecurityConfig` applies `.anyRequest().authenticated()`, so `GET /` requires a valid session. The test `dashboard_unauthenticated_redirectsToLogin` confirms this. No code change needed in the controller for R1.

---

## Correctness Findings

**R2 — attribute extraction:** `principal.getAttribute("name")` and `principal.getAttribute("email")` are correct for both Facebook (top-level attributes) and Twitter (flattened into `DefaultOAuth2User` with `data` as attribute map by `CustomOAuth2UserService`). The null-name fallback to `"Unknown"` is correct and mirrors `createNewUser` logic.

**R5 — Twitter null email:** `model.addAttribute("email", null)` is set when Twitter omits email. The template correctly branches on `${email != null}` / `${email == null}` using paired `th:if` cells. No NPE possible in the template for this path.

**`@AuthenticationPrincipal` + `OAuth2AuthenticationToken`:** Both can be injected as method parameters from the same authentication object. Spring resolves `@AuthenticationPrincipal` to `auth.getPrincipal()` and resolves `OAuth2AuthenticationToken authentication` via `HandlerMethodArgumentResolver`. This pattern is valid.

---

## Acceptance Criteria Map

| Criterion | Status | Notes |
|---|---|---|
| R1 — `GET /` requires auth | PASS | `SecurityConfig` + unauthenticated test |
| R2 — `name`, `email`, `provider` from `OAuth2User` | PARTIAL | Controller correct; `provider` not asserted in tests (I-2) |
| R3 — Logout POSTs to `/logout`, CSRF auto-injected | PASS | `th:action="@{/logout}"` correct |
| R4 — Style consistent with `login.html` | PASS | Same font stack, palette (`#f0f2f5`, `#1a1a2e`, `#6b7280`), card shadow, border-radius |
| R5 — Twitter null email renders "N/A", no NPE | PASS | `th:if` branching is correct |

---

## Positive Observations

- Controller is admirably thin — no business logic leaked in.
- Null-name fallback is consistent with `createNewUser` in `CustomOAuth2UserService`.
- Template uses `th:text` exclusively — no XSS risk.
- `th:action="@{/logout}"` is the correct Thymeleaf idiom for CSRF-safe logout.
- `SecurityConfig` is imported cleanly in the test with `@Import`, so the full security filter chain is exercised.
- `@ActiveProfiles("test")` is set, preventing real DB calls.
- Four-case test coverage (Facebook, Twitter null email, null name, unauthenticated) is solid for the current scope.

---

## Recommended Actions

1. **(Important)** Add `model().attribute("provider", ...)` assertions to at least the Facebook and Twitter tests.
2. **(Important)** Add a null guard for `principal` in the controller method, or document why it is guaranteed non-null.
3. **(Minor)** Remove `@RequiredArgsConstructor` — no constructor injection exists.
4. **(Minor)** Add a comment on the provider badge ternary noting it assumes exactly two providers.

---

**Status:** DONE_WITH_CONCERNS
**Summary:** Dashboard implementation is correct and secure. Two important gaps found: `provider` attribute untested (I-2) and missing null guard on `principal` (I-1). No critical blockers.
**Concerns/Blockers:** I-1 and I-2 should be fixed before merge; neither is a showstopper but both represent real regression risk.

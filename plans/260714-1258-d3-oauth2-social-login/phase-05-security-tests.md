# Phase 5 — Security Integration Tests + Fix Existing Slice Tests

## Context links

- Spec: `plans/260714-1258-d3-oauth2-social-login/spec/oauth2-social-login/technical-spec.md` (SC-001..SC-010)
- Research: `plans/reports/researcher-260714-1150-spring-oauth2-fb-twitter.md` §6
- Scout: `plans/reports/scout-260714-1304-d3-codebase.md` (existing tests + blast radius analysis)

## Overview

| Field | Value |
|-------|-------|
| Priority | P0 |
| Status | completed 2026-07-14 |
| Tasks | D3-08 (CSRF verification), D3-09 (security integration tests + fix existing slice tests) |
| Description | Write `SecurityConfig` integration tests covering all SC-0xx scenarios; update existing `PostControllerTest` (and any other `@WebMvcTest` slice) to satisfy the new security filter chain requirements. |
| Test results | 45/45 passing; all pre-D3 tests + new security tests pass; twitter_dataPresent_butMissingId_throwsOAuth2AuthenticationException added per review |

## Requirements

- SC-001 — unauthenticated GET `/posts` → 302 to `/login`
- SC-002 — POST to protected endpoint without CSRF → 403
- SC-003 — mock OAuth2 login → User row has `role = 'USER'`
- SC-004 — no access token in log output (tested by log capture assertion or review)
- SC-005 — second login same provider → updates existing `SocialAccount` row (no duplicate)
- SC-006 — GET `/login` → 200
- SC-007 — GET `/posts` unauthenticated → 302 to `/login`
- SC-008 — Twitter mock login → `SocialAccount` with `provider=TWITTER`, no `ClassCastException`
- SC-009 — GET `/swagger-ui/index.html` without session → 200
- SC-010 — GET `/logout` authenticated → 302 to `/login?logout`

## Code files

### Existing to modify

- `src/test/java/com/sunasterisk/socialanalytics/controller/PostControllerTest.java` — add `@Import(SecurityConfig.class)`, `@MockitoBean CustomOAuth2UserService`, `@MockitoBean ClientRegistrationRepository`; add `oauth2Login()` to authenticated tests; add `csrf()` to mutating requests
- Any other `@WebMvcTest` slice tests that will break (check: `ImportControllerTest`, `MetricControllerTest`, `ExcelExportControllerTest` if they exist)

### New to create

- `src/test/java/com/sunasterisk/socialanalytics/security/SecurityConfigTest.java`
- `src/test/java/com/sunasterisk/socialanalytics/security/CustomOAuth2UserServiceTest.java`

## Implementation steps

### A — Fix existing `PostControllerTest`

1. Add `@Import(SecurityConfig.class)` to class-level annotations
2. Add `@MockitoBean CustomOAuth2UserService oauth2UserService;`
3. Add `@MockitoBean ClientRegistrationRepository clientRegistrationRepository;`
   (Rationale: `@WebMvcTest` does NOT auto-configure `ClientRegistrationRepository`; context fails to start without it. See research §6 "ClientRegistrationRepository pitfall".)
4. Update `list_returns200WithPageJson()` test:
   - Add `.with(oauth2Login())` to the `mockMvc.perform(get("/posts")...)` call
5. Update `delete_returns204_whenFound()` test:
   - Add `.with(oauth2Login())` and `.with(csrf())` to `mockMvc.perform(delete(...)...)`
6. Update `delete_returns404_whenNotFound()` test:
   - Add `.with(oauth2Login())` and `.with(csrf())`
7. Static imports: add `import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;` and `csrf()`

### B — Scan and fix other `@WebMvcTest` slices

8. Check for other `@WebMvcTest` test classes in the codebase (ImportController, MetricController, ExcelExportController tests if present):
   - Apply the same pattern: `@Import(SecurityConfig.class)`, two `@MockitoBean`s, `oauth2Login()` on authenticated requests, `csrf()` on POST/DELETE

### C — `SecurityConfigTest` (new)

9. Class: `@WebMvcTest` with no specific controller — use `@WebMvcTest` + `@Import(SecurityConfig.class)`
   - `@MockitoBean CustomOAuth2UserService`
   - `@MockitoBean ClientRegistrationRepository`

10. **Test methods**:

   ```
   // SC-006 / FR-005
   loginPage_isPubliclyAccessible() → GET /login → 200

   // SC-007 / FR-001
   unauthenticated_getProtectedPath_redirectsToLogin() → GET /posts → 302 → /login

   // SC-009 / FR-012
   swaggerUi_isPubliclyAccessible() → GET /swagger-ui/index.html → 200

   // SC-002 / BR-004
   postWithoutCsrf_returns403() → POST /import-posts (with oauth2Login(), no csrf()) → 403

   // SC-002 variant: CSRF enforced without authentication too (unauthenticated POST → redirects, not 403)
   // Spring Security redirects unauthenticated POSTs to /login before CSRF evaluation
   // so this test needs oauth2Login() to get past auth, then verify CSRF rejection

   // SC-010 / FR-013
   logout_authenticated_redirectsToLoginLogout() → GET /logout (with oauth2Login()) → 302 → /login?logout

   // unauthenticated: redirect with ?logout param suppressed
   unauthenticated_getPost_redirectsToLogin() → already covered above
   ```

### D — `CustomOAuth2UserServiceTest` (new)

11. Class: plain JUnit5 unit test (`@ExtendWith(MockitoExtension.class)`)
    - `@InjectMocks CustomOAuth2UserService service`
    - `@Mock UserRepository userRepository`
    - `@Mock SocialAccountRepository socialAccountRepository`
    - Spy/mock `DefaultOAuth2UserService` parent's `loadUser()` — use `@Spy` + `doReturn(mockOAuth2User)` or subclass override in test

12. **Test methods**:

    ```
    // SC-003 / BR-001
    facebook_newUser_createdWithRoleUser()
      - Arrange: mock super.loadUser returns OAuth2User with id, name, email attrs
      - Arrange: userRepository.findByEmail returns empty Optional
      - Act: service.loadUser(facebookRequest)
      - Assert: userRepository.save(argThat(u -> u.getRole() == UserRole.USER))

    // SC-005 / BR-003
    facebook_existingUser_socialAccountUpdated()
      - Arrange: userRepository.findByEmail returns existing User
      - Arrange: socialAccountRepository.findByProviderAndProviderAccountId returns existing SocialAccount
      - Act: service.loadUser(facebookRequest)
      - Assert: socialAccountRepository.save(argThat(sa -> sa.getId() == existingId)) // same row updated

    // SC-008 / DEC-001
    twitter_nestedDataUnwrapped_returnsDefaultOAuth2User()
      - Arrange: mock super.loadUser returns OAuth2User with attributes {"data": {"id":"tw-1","name":"Test"}}
      - Act: OAuth2User result = service.loadUser(twitterRequest)
      - Assert: result.getName() == "tw-1" (user-name-attribute = "id" on flattened data map)
      - Assert: no ClassCastException

    // Edge case: Facebook no email → OAuth2AuthenticationException
    facebook_noEmail_throwsOAuth2AuthenticationException()
      - Arrange: mock super.loadUser returns OAuth2User with id, name but NO email attr
      - Act + Assert: assertThrows(OAuth2AuthenticationException.class, () -> service.loadUser(...))
    ```

13. **SC-004 (no token in logs)** — not easily testable with MockMvc; document as a code review check: grep `CustomOAuth2UserService.java` for any logger call that references the access token variable. Accept as manual verification / code review gate.

## Todo checklist

- [ ] Update `PostControllerTest` — add `@Import(SecurityConfig.class)`, two `@MockitoBean`s
- [ ] Update `PostControllerTest` — add `oauth2Login()` to GET test, `oauth2Login()` + `csrf()` to DELETE tests
- [ ] Scan for other `@WebMvcTest` test classes (ImportController, Metric, Export) and apply same pattern
- [ ] Create `SecurityConfigTest.java` — unauthenticated redirect, public paths, CSRF enforcement, logout
- [ ] Create `CustomOAuth2UserServiceTest.java` — role=USER assertion, SocialAccount upsert, Twitter unwrap, Facebook no-email exception
- [ ] Add `spring-security-test` static imports (`oauth2Login()`, `csrf()`) to all affected test classes
- [ ] Run full test suite: `mvn test`; confirm all existing tests + new tests pass
- [ ] Manual log review: confirm access token value does not appear in any log line (BR-002 / SC-004)

## Success criteria

- All pre-D3 tests (PostControllerTest, PostServiceTest, MetricServiceTest, PostRepositoryTest, ExcelImportServiceTest, ReflectionRowWriterTest) still pass after changes
- New `SecurityConfigTest` passes all SC-0xx scenario methods
- New `CustomOAuth2UserServiceTest` passes all SC-003, SC-005, SC-008, email-absent edge case
- `mvn test` exits with BUILD SUCCESS

## Risk assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| `@WebMvcTest` context fails to start — missing `ClientRegistrationRepository` bean | High (known pitfall per research §6) | High — all slice tests broken | Add `@MockitoBean ClientRegistrationRepository` to EVERY `@WebMvcTest` class that imports `SecurityConfig` |
| `oauth2Login()` post-processor requires `spring-security-test` on classpath | High (dep not yet present before Phase 1) | High — compile error | Phase 1 adds `spring-security-test` test dep; Phase 5 depends on Phase 1 completion |
| `CustomOAuth2UserServiceTest` — mocking `super.loadUser()` in a class that extends `DefaultOAuth2UserService` | Medium | Medium — brittle mock | Use `@Spy` on the service + `doReturn(mockOAuth2User).when(service).callSuper(...)` or refactor to delegate pattern instead of extend (KISS: extending is fine; mock with `doReturn` + `@Spy`) |
| Existing `PostControllerTest` delete tests — DELETE without CSRF used to pass (no security before D3); now requires CSRF | High (known blast radius per scout) | Medium — test failures | Add `.with(csrf())` to all DELETE + POST mock requests in existing tests |

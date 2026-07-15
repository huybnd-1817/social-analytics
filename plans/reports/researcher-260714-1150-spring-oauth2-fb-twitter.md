# Research Report: Spring Boot 4.1 / Spring Security 7 — OAuth2 Social Login (Facebook + Twitter/X)

**Date:** 2026-07-14 | **Stack:** Spring Boot 4.1.0, Java 26, Spring Security 7.1, Thymeleaf (to be added)
**Sources:** Spring official docs, Spring Security GitHub issues, Maven Central, Spring Boot release notes, Spring guides

---

## 1. Dependencies

```xml
<!-- Required starters — version managed by Spring Boot 4.1 BOM -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
<!-- Thymeleaf sec:* dialect — Spring Boot BOM manages version.
     Artifact name is still -springsecurity6 even under SS7;
     Spring Boot 4.x BOM pins a compatible version. Verify at build time. -->
<dependency>
    <groupId>org.thymeleaf.extras</groupId>
    <artifactId>thymeleaf-extras-springsecurity6</artifactId>
</dependency>
<!-- Test -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

**Note on thymeleaf-extras artifact:** The `thymeleaf-extras-springsecurity` repo was archived April 2026; code merged into main Thymeleaf repo. The artifact `thymeleaf-extras-springsecurity6` v3.1.5.RELEASE is the last standalone release. Spring Boot 4.x BOM manages compatibility — do NOT specify a version explicitly; let BOM handle it. If BOM does not resolve it, check if `spring-boot-starter-thymeleaf` auto-activates the dialect without the extras artifact.

---

## 2. Facebook — CommonOAuth2Provider (Built-in)

**Status:** Built-in `CommonOAuth2Provider.FACEBOOK`. Only `client-id` and `client-secret` required.

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          facebook:
            client-id: ${FACEBOOK_CLIENT_ID:placeholder}
            client-secret: ${FACEBOOK_CLIENT_SECRET:placeholder}
            # scope default: public_profile (no email by default)
            # To get email, explicitly add:
            scope: public_profile,email
```

**Built-in defaults (from CommonOAuth2Provider.FACEBOOK):**
- `authorization-uri`: `https://www.facebook.com/v18.0/dialog/oauth`
  - **Graph API version caveat:** Spring's built-in definition targets an older Graph API version (v12 or v18 depending on Spring Security release). Facebook deprecates old versions; confirm the embedded version matches your FB app's minimum supported version. To pin a specific version, override provider properties.
- `token-uri`: `https://graph.facebook.com/v18.0/oauth/access_token`
- `user-info-uri`: `https://graph.facebook.com/me?fields=id,name,email,picture`
- `user-name-attribute`: `id`

**User-info attributes returned:** `id`, `name`, `email` (if `email` scope requested), `picture` (object).

**Trade-off:** `email` is not in the default scope; add `scope: public_profile,email` explicitly or users cannot be uniquely identified by email.

---

## 3. Twitter/X — Manual Provider (NOT Built-in)

**Status as of 2026-07-14:** Issue #16379 (opened Jan 2025) requested adding X to `CommonOAuth2Provider`. Still **open, unmerged** — X is NOT a built-in provider in any released Spring Security version.

**Spring Security 7 key change:** PKCE is now **enabled by default** for all authorization code flows. This aligns perfectly with X's PKCE requirement.

### Full provider registration:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          twitter:
            client-id: ${TWITTER_CLIENT_ID:placeholder}
            client-secret: ${TWITTER_CLIENT_SECRET:placeholder}
            authorization-grant-type: authorization_code
            # For confidential clients (server-side app): client-secret-basic or client-secret-post
            # For public clients (no secret storage): none — triggers PKCE-only flow
            client-authentication-method: client-secret-post
            scope: users.read,tweet.read,offline.access
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            provider: twitter
        provider:
          twitter:
            authorization-uri: https://x.com/i/oauth2/authorize
            token-uri: https://api.twitter.com/2/oauth2/token
            user-info-uri: https://api.twitter.com/2/users/me
            user-info-authentication-method: header
            user-name-attribute: id
```

**PKCE in Spring Security 7:** Enabled automatically for all code flows — no extra config needed. In SS6, PKCE required `client-authentication-method: none`; in SS7 it is unconditional.

**Scopes:**
- `users.read` — required for user profile
- `tweet.read` — required for timeline access
- `offline.access` — required for refresh tokens

**user-name-attribute:** X returns `{"data": {"id": "...", "name": "...", "username": "..."}}`. The top-level attribute is `data`, not `id`. Using `user-name-attribute: id` on a nested response **will fail** — see section 4 for the fix.

---

## 4. Custom OAuth2UserService Pattern

### The nested-data problem (X-specific)

X's `/2/users/me` response:
```json
{
  "data": {
    "id": "123456",
    "name": "John Doe",
    "username": "johndoe"
  }
}
```

`DefaultOAuth2UserService` reads attributes from the top level. Setting `user-name-attribute: data` gives you a Map, not a String. **A custom service is mandatory for X.**

### Implementation pattern

```java
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String accessToken = userRequest.getAccessToken().getTokenValue();

        String provider = registrationId; // "facebook" or "twitter"
        String providerUserId;
        String name;

        if ("twitter".equals(provider)) {
            // Unwrap nested {"data": {...}}
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) oAuth2User.getAttributes().get("data");
            providerUserId = (String) data.get("id");
            name = (String) data.get("name");
            // Return a new OAuth2User with flattened attributes
            return new DefaultOAuth2User(
                oAuth2User.getAuthorities(),
                data,
                "id"
            );
        } else {
            providerUserId = oAuth2User.getAttribute("id").toString();
            name = oAuth2User.getAttribute("name");
        }

        // Upsert domain User + SocialAccount
        upsertUser(provider, providerUserId, name, accessToken);

        return oAuth2User;
    }

    private void upsertUser(String provider, String providerUserId, String name, String accessToken) {
        // find or create SocialAccount → link to User
        // accessToken stored here if needed for downstream API calls
    }
}
```

**Where success handler fits:** `OAuth2UserService.loadUser()` is called **during** authentication; it is the right place for upsert. `AuthenticationSuccessHandler` is called **after** authentication succeeds and is the right place for post-login redirect logic (e.g. redirect new users to profile completion page vs returning users to dashboard). Do not mix persistence into the success handler — keep it in the user service.

**Accessing access token:** `userRequest.getAccessToken().getTokenValue()` in `loadUser()`. If you need it later (e.g., for API calls after login), persist it to `SocialAccount.accessToken` during upsert.

---

## 5. SecurityConfig — Spring Security 7 Lambda DSL

**SS7 breaking changes vs SS6:**
- `and()` chaining removed — use lambda DSL only (already required in SS6.1+)
- `authorizeRequests()` removed — use `authorizeHttpRequests()`
- `HttpSecurity.apply()` removed — use `.with()`
- PKCE default-on for all code flows

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            CustomOAuth2UserService oauth2UserService) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/login", "/login/**",
                    "/oauth2/**", "/login/oauth2/**",
                    "/swagger-ui/**", "/v3/api-docs/**",
                    "/css/**", "/js/**", "/images/**", "/webjars/**",
                    "/error"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(oauth2UserService)
                )
                .successHandler(authenticationSuccessHandler())
                .failureHandler(authenticationFailureHandler())
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .csrf(csrf -> csrf
                // Session-based CSRF (default) is fine for Thymeleaf server-side rendering.
                // Thymeleaf auto-injects _csrf into th:action forms when security integration active.
                // CookieCsrfTokenRepository needed ONLY if JS (SPA) reads the token from cookie.
                // For pure Thymeleaf: keep default HttpSessionCsrfTokenRepository.
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                // XorCsrfTokenRequestAttributeHandler (BREACH protection) is default in SS6+/SS7.
                // For Thymeleaf: use CsrfTokenRequestAttributeHandler (plain, no XOR) only if
                // JS reads token from th:meta and sends raw value — XOR tokens change on each request.
                // Safe default for pure server-side Thymeleaf: leave as XorCsrfTokenRequestAttributeHandler.
                .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler())
            );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            // e.g., redirect new users to /profile/setup, returning users to /dashboard
            response.sendRedirect("/dashboard");
        };
    }

    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return (request, response, exception) ->
            response.sendRedirect("/login?error");
    }
}
```

**CSRF note:** For pure server-side Thymeleaf, default `HttpSessionCsrfTokenRepository` + `XorCsrfTokenRequestAttributeHandler` is correct. `CookieCsrfTokenRepository.withHttpOnlyFalse()` only needed if JS reads the token. Thymeleaf `th:action` on forms auto-injects the CSRF hidden input when `thymeleaf-extras-springsecurity6` is on the classpath.

---

## 6. Testing with MockMvc

```java
@WebMvcTest(SomeController.class)
@Import(SecurityConfig.class)
class SomeControllerTest {

    @Autowired MockMvc mockMvc;

    // In Spring Boot 4.x tests use @MockitoBean (not @MockBean which is removed)
    @MockitoBean CustomOAuth2UserService oauth2UserService;
    @MockitoBean ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void authenticated_accessesDashboard() throws Exception {
        mockMvc.perform(get("/dashboard")
                .with(oauth2Login()
                    .attributes(attrs -> attrs.put("id", "fb-user-123"))
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                ))
            .andExpect(status().isOk());
    }

    @Test
    void postWithoutCsrf_rejected() throws Exception {
        mockMvc.perform(post("/some-action")
                .with(oauth2Login()))
            .andExpect(status().isForbidden());
    }

    @Test
    void postWithCsrf_accepted() throws Exception {
        mockMvc.perform(post("/some-action")
                .with(oauth2Login())
                .with(csrf()))
            .andExpect(status().isOk());
    }
}
```

**ClientRegistrationRepository pitfall:** `@WebMvcTest` does NOT auto-configure `ClientRegistrationRepository`. Without it, the context fails to start. Options:
1. `@MockitoBean ClientRegistrationRepository clientRegistrationRepository;` (simplest)
2. Add `spring.security.oauth2.client` properties in `src/test/resources/application-test.yml` with placeholder values — Boot auto-creates the bean from properties

**`oauth2Login()` post-processor** does NOT hit real OAuth flows — it injects an `OAuth2AuthenticationToken` directly into the test `SecurityContext`. No `ClientRegistrationRepository` bean needed for the post-processor itself, only for context initialization.

---

## 7. Placeholder Client-IDs for Local Dev

```yaml
# application.yml
spring:
  security:
    oauth2:
      client:
        registration:
          facebook:
            client-id: ${FACEBOOK_CLIENT_ID:placeholder-fb}
            client-secret: ${FACEBOOK_CLIENT_SECRET:placeholder-fb-secret}
          twitter:
            client-id: ${TWITTER_CLIENT_ID:placeholder-tw}
            client-secret: ${TWITTER_CLIENT_SECRET:placeholder-tw-secret}
            # ... other props
```

**Startup behavior with placeholders:** Spring Boot validates that `client-id` and `client-secret` are non-empty strings at startup — `placeholder-fb` satisfies this. The app boots and the login page renders. The OAuth flow fails at runtime when a user clicks "Login with Facebook" (redirect goes to Facebook with a fake client-id, Facebook rejects). **No startup crash.**

**Edge case:** If `${FACEBOOK_CLIENT_ID}` resolves to an empty string (`""`), Spring Boot **will** throw a `BeanCreationException` at startup because empty strings fail the non-empty validation. Always provide a non-empty default: `${VAR:placeholder}`.

---

## Trade-off Summary

| Concern | Facebook | Twitter/X |
|---|---|---|
| CommonOAuth2Provider | Yes — minimal config | No — full manual registration |
| PKCE | Not required | Required (SS7: auto-enabled) |
| user-info nesting | Flat — DefaultOAuth2UserService OK | Nested `data` — custom service mandatory |
| email availability | Requires `email` scope explicitly | Not available from user-info endpoint |
| Graph API version | Embedded in Spring — may lag Facebook's deprecation schedule | N/A |
| Token auth method | CLIENT_SECRET_BASIC (default) | CLIENT_SECRET_POST (X requirement) |

---

## Adoption Risk

| Component | Risk |
|---|---|
| Spring Security 7.1 | Low — GA, backed by VMware/Broadcom, aligns with Boot 4.1 |
| thymeleaf-extras-springsecurity6 under SS7 | Medium — repo archived Apr 2026; moved to main Thymeleaf repo; BOM compatibility unverified at research time |
| X OAuth2 provider | Medium — X API policy changes frequently; PKCE default-on in SS7 mitigates config drift |
| CommonOAuth2Provider.FACEBOOK Graph API version | Low-Medium — Spring updates lag Facebook's deprecation cycles; pin Graph API version if using v20+ features |

---

## Unresolved Questions

1. **thymeleaf-extras artifact under Boot 4.1:** Does Spring Boot 4.1's BOM pin a version of `thymeleaf-extras-springsecurity6` that is compatible with Spring Security 7.1? The repo was archived after the standalone artifact's last release (3.1.5.RELEASE). Verify at build time: run `mvn dependency:tree | grep thymeleaf-extras` and confirm no runtime `NoSuchMethodError` from Security API mismatches.
2. **Facebook Graph API version in CommonOAuth2Provider:** What specific version does Spring Security 7.1's `CommonOAuth2Provider.FACEBOOK` embed? Confirm against Facebook's current minimum supported version (check `CommonOAuth2Provider.java` source).
3. **X `client-authentication-method`:** Confidential server-side apps can use `client-secret-post` or `client-secret-basic`. X's docs currently specify Basic Auth for confidential clients — verify the token exchange actually works with `client-secret-basic` vs `client-secret-post`.
4. **`offline.access` scope for X:** Refresh token behavior — test whether X actually returns a `refresh_token` and whether Spring's `JdbcOAuth2AuthorizedClientService` handles token refresh for this provider.

---

## Sources

- [Spring Boot 4.1 OAuth2 Docs](https://docs.spring.io/spring-boot/reference/security/oauth2.html)
- [Spring Security OAuth2 Login Core](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html)
- [Spring Security OAuth2 MockMvc Testing](https://docs.spring.io/spring-security/reference/servlet/test/mockmvc/oauth2.html)
- [Spring Security 7.0 Migration Guide](https://deepwiki.com/spring-projects/spring-security/9-migration-to-spring-security-7.0)
- [GitHub Issue #16379 — Add X to CommonOAuth2Provider](https://github.com/spring-projects/spring-security/issues/16379)
- [Spring Boot OAuth2 Tutorial](https://spring.io/guides/tutorials/spring-boot-oauth2/)
- [Spring Boot 4.1 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes)
- [thymeleaf-extras-springsecurity (archived)](https://github.com/thymeleaf/thymeleaf-extras-springsecurity)
- [Spring CSRF Docs](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)

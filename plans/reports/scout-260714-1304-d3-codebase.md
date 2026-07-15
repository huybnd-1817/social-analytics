# Scout Report — Codebase state before D3 (OAuth2 social login)

## Stack
- Maven, Spring Boot **4.1.0**, Java **26** → Spring Security 7.x when added. springdoc-openapi 3.0.3, Apache POI 5.3.0, Lombok, Flyway.
- NO security/oauth2/thymeleaf deps yet. No SecurityConfig, no templates, no login page. All endpoints public.
- Tests: JUnit5, `@WebMvcTest`/`@DataJpaTest`/`@SpringBootTest`, H2 in-mem (MODE=PostgreSQL), `@MockitoBean` (Boot 4.x).

## Base package
`com.sunasterisk.socialanalytics` — layout: controller/ (5), service/ (5), repository/ (5), entity/ (10), config/ (OpenApiConfig, JpaAuditingConfig), dto/ (6), util/ (4).

## Entities (Flyway V1 schema already in place, ddl-auto=none)
- **User**: id, email (UNIQUE NOT NULL), name, avatarUrl, role UserRole enum **default ADMIN** [ADMIN/USER], createdAt/updatedAt (BaseEntity, JPA auditing).
- **SocialAccount**: id, user (ManyToOne LAZY), provider SocialProvider enum [FACEBOOK/TWITTER], providerAccountId, accessToken (TEXT NOT NULL), refreshToken (nullable), tokenExpiresAt (nullable), createdAt.
- Post, SocialMetric, ImportBatch (not touched by D3).
- Repositories exist incl. `UserRepository.findByEmail()`, `SocialAccountRepository.findByProviderAndProviderAccountId()`.

## Web layer (all @RestController)
- GET /posts (paginated), DELETE /posts/{id}
- GET /metrics
- POST /import-posts (MultipartFile)
- GET /export-report (.xlsx download)
- Swagger at /swagger-ui.html, /v3/api-docs

## Config
- `spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}`; dev = localhost postgres; prod = env vars (${DB_URL} etc.), `server.forward-headers-strategy=framework`; test profile = H2 + flyway off + ddl-auto=create-drop.
- `spring.thymeleaf.prefix` already set in base properties (Thymeleaf dep not yet added).

## Blast radius for D3
- Adding security filter chain makes ALL endpoints authenticated → existing `PostControllerTest` (@WebMvcTest) and any MockMvc slice tests will 401/403 unless updated (mock ClientRegistrationRepository, import SecurityConfig or use oauth2Login()/csrf() post-processors).
- POST/DELETE endpoints now need CSRF tokens → affects API consumers/Swagger try-it-out.
- No dashboard page exists (D4/D5 scope) — post-login redirect `/` currently has no UI mapping.

## Deferred security traps (260710 adversarial review — MUST address in D3)
1. `role` DEFAULT 'ADMIN' in DB + entity: OAuth2 upsert MUST set role explicitly or every login mints an admin.
2. Token leak: never raise `org.hibernate.orm.jdbc.bind` logging to TRACE once real tokens are stored.
3. Tokens plaintext at rest — design doc says encrypt (Jasypt/AES) before prod; D3 may defer with explicit note.
4. XFF: prod `forward-headers-strategy=framework` trusts X-Forwarded-* from any source — relevant when auth/rate-limiting lands.
5. Dev-profile fail-open default (user-accepted trade-off; do not re-litigate).

# Review Report — /review-code --pending (D2 test suite)

Date: 2026-07-13 | Branch: feature/unit-test | Mode: pending (staged + untracked) | Level: medium

## Scope
Code: pom.xml (+3 test deps), application-test.properties, PostServiceTest, MetricServiceTest, PostRepositoryTest, PostControllerTest. Docs/plans markdown staged alongside (out of code-review scope).

## Verdicts
- Stage 1 Spec compliance (F001 FR-001..009): PASS — surgical diffs, no src/main changes, extras justified.
- Stage 2 Quality (reviewer): PASS_WITH_ISSUES — 0 critical, 2 important, 4 minor.
- Stage 3 Adversarial (mandatory — new deps): 10 findings → 6 Accept, 2 Reject, 2 Defer.

## Accepted + FIXED (this session, verified 15/15 green, exit 0)
1. H1 shared named H2 `testdb` + DB_CLOSE_DELAY=-1 cross-context trap → URL now `jdbc:h2:mem:${random.uuid};MODE=PostgreSQL;...`
2. M1/S2#1 dirty-check flush unproven at DB level → `PostRepositoryTest.softDelete_persistsStatusChange` added
3. S2#3 COUNT query never fired → `PostRepositoryTest.findByStatus_pagination_runsCountQuery` added (2 ACTIVE, page size 1)
4. M2 @PageableDefault contract unverified → argThat verify (size 20, createdAt DESC) in PostControllerTest
5. S2#2 profile inconsistency → @ActiveProfiles("test") on PostControllerTest
6. M4/S2#4 partial mapper assertions → full PostResponse field lock in PostServiceTest (+publishedAt fixture); +platformPostId/title jsonPath in controller test; nullable-omission comment in repo fixture

## Rejected (documented false positives)
- M3 @Import(GlobalExceptionHandler): @RestControllerAdvice pickup in @WebMvcTest is documented Spring Boot contract; 404 test fails loudly if handler vanishes.
- S2#6 magic literals in MetricServiceTest: distinct values ARE the field-swap detector; constants = ceremony (YAGNI).

## Deferred (tracked for Day 6 Postgres integration suite — D6-09..11)
- H2 partial unique index `uk_platform_post_active` unenforceable on H2 (Flyway off in slice): duplicate-active-post uniqueness must be proven on real Postgres (Testcontainers or integration profile).
- PG enum-domain serialization drift (NAMED_ENUM): H2 accepts strings PG would reject; verify enum round-trip on Postgres.
- `GET /posts?sort=bad` → 400 (PropertyReferenceException path): untriggerable through stubbed service in web slice; needs integration test.

## Verification
`./mvnw test` fresh run post-fix: Tests run 15, Failures 0, Errors 0, BUILD SUCCESS, exit 0.

## Unresolved questions
- None blocking. Deferred items above must land with Day-6 integration tests or the uniqueness/enum gaps stay unproven against real Postgres.

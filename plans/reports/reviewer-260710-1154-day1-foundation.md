# Code Review — Day-1 Foundation (Stage 2)

> Reconstructed 260710-20:20 from review session findings (original file lost to git reset incident). Findings verbatim from reviewer agent (fable) result; full prose analysis not preserved.

Scope: pending changes vs fa207e2 (29 files, +852). Compile verified PASS. Layering, transaction boundaries, OSIV-off + in-transaction DTO mapping, V1 migration: sound. 0 Critical.

## Important

- I1 — GlobalExceptionHandler — invalid `?sort=` property → unhandled PropertyReferenceException → 500 instead of 400; `sort=user.email` silently orders by related-entity fields. **FIXED: handler added (400)**
- I2 — PostController/MetricController — direct `Page<T>` serialization = unstable JSON contract. **FIXED: `spring.data.web.pageable.serialization-mode=via_dto`**
- I3 — V1 migration — no index serves `posts WHERE status ORDER BY created_at`; global metrics sort unindexed. **FIXED: `idx_posts_status_created`; metrics index deferred (endpoint may be placeholder)**
- I4 — entity/DDL nullability drift: SocialAccount.accessToken, ImportBatch+SocialMetric count columns, ImportBatch.importedAt never set → insert crash on Day 2. **FIXED: nullable=false aligned; @CreatedDate on importedAt**
- I5 — .gitignore hid application-{dev,prod}.properties (no secrets inside; fresh clone can't boot). **FIXED: un-ignored**

## Minor (fixed where noted)

- M1 default-to-dev fail-open (kept — user decision); M2 dual updated_at writers (rejected post-Instant fix); M3 ASC default sort (**fixed: DESC**); M4 no max-page-size cap (**fixed: 100**); M5 Thymeleaf config w/o starter (**annotated, starter Day 3**); M6 explicit dialect (**removed**); M7 stale security logging/comments (**removed/reworded**); M8 baseline-on-migrate (**removed**); M9 exception in util/, @Repository redundant, no 500 fallback (partial — 409/400 handlers added; catch-all rejected: would regress 405→500); M10 entity setters expose setId (noted); M11 LocalDateTime zone-less (**fixed: Instant everywhere**); M12 personal email in OpenApiConfig (**removed**)

## Forward note (Day 3)

Plaintext OAuth tokens in social_accounts — plan attribute-level encryption before real tokens land.

## Unresolved questions

1. Global `uk_platform_post` vs multi-user tracking of same post (partially resolved: partial unique index on ACTIVE)
2. `role DEFAULT 'ADMIN'` vs Day-3 registration flow
3. Is global GET /metrics a real endpoint or placeholder?

**Status:** DONE — 0 Critical, 5 Important, 12 Minor

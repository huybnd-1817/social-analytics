# Adversarial Review — Day-1 Foundation (Stage 3)

> Reconstructed 260710-20:20 from review session findings (original file lost to git reset incident). Findings verbatim from adversarial reviewer (fable) result; adjudication by orchestrator.

Compile verified. Anchors held (auth=Day3, tests=Day2, versions verified).

## Findings + adjudication

| # | Sev | Finding | Verdict |
|---|-----|---------|---------|
| A1 | HIGH | JVM non-UTC: `@CreatedDate` LocalDateTime writes local wall-clock labeled UTC while DB trigger writes true UTC → same row skewed 7h | **Accept — FIXED: all temporal fields → Instant** |
| A2 | MED | Soft-delete keeps `uk_platform_post` occupied → re-import same platform_post_id → permanent block + raw 500 | **Accept — FIXED: partial unique index (WHERE status='ACTIVE') + 409 handler** |
| A3 | MED | Unset SPRING_PROFILES_ACTIVE silently boots dev in prod (fail-open) | **Defer — user's explicit convenience choice, documented** |
| A4 | MED | `forward-headers-strategy=framework` trusts spoofable X-Forwarded-* from any source | **Defer Day 3 — warning comment added; trusted-proxies required before IP rate-limit** |
| A5 | MED | `jdbc.bind=TRACE` will print OAuth tokens to dev logs when Day-3 persists SocialAccount | **Accept — FIXED: TRACE→DEBUG + warning comment** |
| A6 | MED | `?size=2000` × unbounded TEXT content → resource exhaustion | **Accept — FIXED: max-page-size=100** |
| A7 | MED | `role DEFAULT 'ADMIN'` fail-open at DB + entity (spec-mandated) | **Defer — spec revisit recommended before Day 3** |
| A8 | LOW | baseline-on-migrate=true masks wrong-DB target | **Accept — FIXED: removed** |
| A9 | LOW | trigger vs @LastModifiedDate double-write skew | **Reject — post-Instant skew is microseconds; trigger spec-mandated** |
| A10 | LOW | No logging/generic 500 in exception handler; prod log path may be unwritable | **Partial — 400/409 handlers logged; catch-all rejected (405→500 regression)** |
| A11 | LOW | Thymeleaf config dead (no starter in pom) | **Defer Day 3 — annotated** |
| A12 | LOW | Personal email in api-docs | **Accept — FIXED: removed** |

## Unresolved

1. JVM zone pinned anywhere? (moot after Instant fix)
2. Is re-import of soft-deleted posts intended flow? (now allowed)
3. Header-stripping proxy guaranteed in all prod topologies?

**Status:** DONE_WITH_CONCERNS — 1 High, 6 Medium, 5 Low; all High/Medium accepted items fixed same day

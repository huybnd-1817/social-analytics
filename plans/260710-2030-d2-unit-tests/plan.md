---
title: "D2 Unit/Slice Test Suite (D2-01..D2-05)"
description: "Test-only suite: H2 dep + PostService/MetricService unit, PostRepository slice, PostController MVC tests"
status: completed
priority: P1
effort: 4h
branch: feature/unit-test
work_type: feature
spec: docs/features/F001_PostMetricTestSuite/
tags: [testing, spring-boot, junit5, mockito, datajpatest, webmvctest]
created: 2026-07-10
---

# D2 Unit/Slice Test Suite

Automated test infrastructure for the Social Analytics backend. Tests ONLY — no
production code changes. Covers the paginated-list + soft-delete lifecycle of
posts and the paginated-list of metrics across three test layers.

## Scope (from docs/task-breakdown.md, Day 2)

- **D2-01** Add `com.h2database:h2` `<scope>test</scope>` to `pom.xml`
- **D2-02** `PostServiceTest` — Mockito unit (findAll, deleteById success, deleteById not-found)
- **D2-03** `MetricServiceTest` — Mockito unit (findAll mapping + pagination)
- **D2-04** `PostRepositoryTest` — `@DataJpaTest` on H2 (findByStatus, findByIdAndStatus)
- **D2-05** `PostControllerTest` — `@WebMvcTest` MockMvc (GET 200, DELETE 204, DELETE 404)

Out of scope: no `create` on PostService, no new MetricService methods, no changes
to `SocialAnalyticsApplicationTests`.

## Phases

| # | Phase | Status | Covers | Depends on |
|---|-------|--------|--------|------------|
| 01 | [Test infrastructure](phase-01-test-infrastructure.md) | completed | D2-01, FR-001 | — |
| 02 | [Test classes](phase-02-test-classes.md) | completed | D2-02..05, FR-002..009, SC-001..008 | Phase 01 |

## Key Dependencies

- Phase 02 depends on Phase 01: `@DataJpaTest` (D2-04) cannot boot without H2 (D2-01)
  and the Flyway-disabled test profile.
- Unit tests (D2-02, D2-03, D2-05) technically compile without H2 but are grouped in
  Phase 02 to run the whole suite green in one pass.

## Success (observable)

- `./mvnw test` compiles and all new test classes pass without a running Postgres.
- Existing `SocialAnalyticsApplicationTests` untouched (still needs local Postgres — expected).
- 8 named test methods pass, mapping to SC-001..SC-008 in the spec.

## Conventions

- Tests mirror main packages under `src/test/java/com/sunasterisk/socialanalytics/`.
- File names mirror the class under test + `Test` suffix (e.g. `PostServiceTest`).
- Spring Boot 4.x: use `@MockitoBean` (NOT the removed `@MockBean`).

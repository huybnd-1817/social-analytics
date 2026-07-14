---
title: "Phase 01 — POI Dependency + Multipart Config + Repository Methods"
feature: F002, F003
tasks: [D2-06]
status: completed
priority: P1
effort: 1h
---

# Phase 01 — POI Dependency + Multipart Config + Repository Methods

## Context Links

- Study §9 (POI dep), §8 risks #1, #2, #4: `plans/reports/researcher-260713-1149-excel-import-export-study.md`
- Import spec Assumptions (multipart limits): `spec/excel-bulk-import-posts/technical-spec.md`
- Export spec (findByStatus active list): `spec/excel-export-report/technical-spec.md`

## Overview

**Priority:** P1 · **Status:** completed

Foundation phase. Add Apache POI, configure multipart limits, and add BOTH new
`PostRepository` methods here so Phases 02 and 03 never touch the same file (parallel-safe).
Blocks 02 and 03.

## Key Insights

- POI is absent from `pom.xml`; build cannot read/write `.xlsx` without it. `poi-ooxml` 5.3.0
  transitively pulls `poi` core — declare only `poi-ooxml`.
- Spring Boot default multipart max-file-size is 1MB — too small for real `.xlsx`. Resolved
  decision: set in base `application.properties` (all envs), 10MB file / 11MB request.
- `PostRepository.findByStatus` currently only has a `Pageable` overload. Export needs an
  unbounded list; import needs a bulk active-key lookup. Both added here to avoid a shared-file
  clash between the two parallel phases.

## Requirements

- FR-001 (import, multipart 10MB), FR-002 (export, ACTIVE-only selection) — supporting infra.

## Related Code Files

**Modify:**
- `pom.xml` — add `poi-ooxml` 5.3.0 dependency.
- `src/main/resources/application.properties` — add multipart limits.
- `src/main/java/com/sunasterisk/socialanalytics/repository/PostRepository.java` — add two methods.

**Create:** none.

## Implementation Steps

1. Add to `pom.xml` `<dependencies>`:
   ```xml
   <dependency>
       <groupId>org.apache.poi</groupId>
       <artifactId>poi-ooxml</artifactId>
       <version>5.3.0</version>
   </dependency>
   ```
2. Add to base `application.properties`:
   ```
   spring.servlet.multipart.max-file-size=10MB
   spring.servlet.multipart.max-request-size=11MB
   ```
3. Add to `PostRepository`:
   - `List<Post> findByStatus(PostStatus status);` (list overload — export, FR-002)
   - `List<Post> findByStatusAndPlatformAndPlatformPostIdIn(PostStatus status, SocialProvider platform, Collection<String> ids);`
     — bulk active-duplicate lookup for import BR-003. (Or a `@Query` over the composite key;
     keep it a derived query if the pair-set is filtered in-service.)
4. Run `./mvnw -q compile` — confirm POI resolves and repo compiles.

## Todo List

- [x] Add `poi-ooxml` 5.3.0 to `pom.xml`
- [x] Add multipart limits to base `application.properties`
- [x] Add `findByStatus(PostStatus)` list overload to `PostRepository`
- [x] Add active-duplicate bulk lookup method to `PostRepository`
- [x] `./mvnw -q compile` passes; POI on classpath

## Success Criteria

- `./mvnw -q compile` succeeds; `XSSFWorkbook` importable in a scratch class.
- `mvn dependency:tree` shows `poi-ooxml:5.3.0` and transitive `poi`.
- Both new repository methods compile.

## Risk Assessment

| Risk | Likelihood | Impact | Countermove |
|------|-----------|--------|-------------|
| POI version yanked / not in repo | Low | High | 5.3.0 is stable per study; fall back to latest 5.3.x |
| Multipart 413 vs 400 behavior differs by Boot version | Low | Low | Documented in edge-cases; handled in Phase 02 handler |

## Rollback

Revert `pom.xml`, `application.properties`, and `PostRepository` edits — no schema/data change.

## Next Steps

Unblocks Phase 02 (import) and Phase 03 (export), which then run in parallel.

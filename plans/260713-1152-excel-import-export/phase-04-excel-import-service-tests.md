---
title: "Phase 04 — ExcelImportService Unit Tests"
feature: F002
tasks: [D2-11]
status: done
priority: P1
effort: 1h
---

# Phase 04 — ExcelImportService Unit Tests

## Context Links

- Import spec verification (SC-001..004): `spec/excel-bulk-import-posts/technical-spec.md`
- Study §6 (test conventions), §8 risk #7 (H2 + NAMED_ENUM incompatible)
- Existing pattern: `src/test/java/com/sunasterisk/socialanalytics/service/PostServiceTest.java`

## Overview

**Priority:** P1 · **Status:** completed · **Depends on:** Phase 02

Unit-test `ExcelImportService` with **pure Mockito** (NO `@DataJpaTest` — H2 cannot handle the
`NAMED_ENUM` JDBC type). Cover: valid file, missing columns, empty file.

## Key Insights

- **NO Spring context / NO H2.** `@ExtendWith(MockitoExtension.class)`, `@Mock` repos +
  `ImportBatchService`, `@InjectMocks ExcelImportService`. Consistent with `PostServiceTest`.
- **Build `.xlsx` fixtures in-memory with POI** inside the test (create `XSSFWorkbook`, write
  rows, serialize to `byte[]`, wrap as `MockMultipartFile`).
- Follow conventions: `methodName_condition_expectedOutcome`, AssertJ `assertThat`,
  `assertThrows`, Lombok `@Builder` fixtures. `@CreatedDate` will NOT fire (no Spring context).
- Mock `postRepository` dup-lookup + `saveAll`; mock seed-user resolution.

## Requirements

Verifies SC-001 (valid), part of SC-003 (empty/missing columns) for F002. FR-002, FR-006, BR-001..006.

## Related Code Files

**Create:**
- `src/test/java/com/sunasterisk/socialanalytics/service/ExcelImportServiceTest.java`

**Read for context (do NOT edit):**
- `service/ExcelImportService.java`, `util/ExcelRowMapper.java`, `dto/ImportBatchResponse.java`
  (from Phase 02); `PostServiceTest.java` (pattern).

## Implementation Steps

1. Private helper `xlsx(headers, rows...)` → `MockMultipartFile` built via POI `XSSFWorkbook`.
2. `importPosts_validFile_persistsAllAndReturnsDone`: 2-3 valid rows; mock no dup, `saveAll`
   echoes; assert status DONE, `successRecords` = row count, `saveAll` invoked once.
3. `importPosts_missingRequiredColumns_throwsBadRequest`: header lacks `platform_post_id`;
   assert `IllegalArgumentException` (message mentions missing columns), `saveAll` never called.
4. `importPosts_emptyFile_throwsBadRequest`: header-only / 0 data rows; assert
   `IllegalArgumentException`, no batch persisted, `saveAll` never called.
5. `./mvnw -q test -Dtest=ExcelImportServiceTest`.

## Todo List

- [x] In-memory `.xlsx` fixture helper (POI) — extracted to ExcelFixtureBuilder
- [x] Test: valid file → DONE + persist
- [x] Test: missing columns → IllegalArgumentException, no persist
- [x] Test: empty file → IllegalArgumentException, no persist
- [x] `./mvnw -q test -Dtest=ExcelImportServiceTest` green — 8/8 pass

## Success Criteria

- 3 tests pass, pure Mockito (no Spring context, no H2).
- `verify(postRepository, never()).saveAll(any())` on both failure cases.
- Naming + AssertJ conventions match `PostServiceTest`.

## Risk Assessment

| Risk | Likelihood | Impact | Countermove |
|------|-----------|--------|-------------|
| Accidentally pulling Spring/H2 context → NAMED_ENUM failure | Med | High | Strictly Mockito; no `@SpringBootTest`/`@DataJpaTest` |
| Fixture `.xlsx` build verbose → test file > 200 lines | Med | Low | Extract fixture helper; keep one concern per test |
| Service internals differ from plan (Phase 02 drift) | Med | Med | Read actual Phase-02 signatures before writing mocks |

## Rollback

Delete the test file. No production code touched.

## Next Steps

Feature complete. Hand final code to reviewer per primary-workflow.

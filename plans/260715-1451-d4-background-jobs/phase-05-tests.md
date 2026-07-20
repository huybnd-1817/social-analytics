# Phase 05 — Crawler Unit Tests (D4-09)

## Context Links
- Spec: `spec/automated-crawl-job/technical-spec.md` (R11; A4)
- Depends on: Phase 02 (`SocialCrawlerService`) — NOT on 03/04; runs in parallel with them
- Existing test pattern: `src/test/java/.../service/{MetricServiceTest,PostServiceTest}.java`

## Overview
- **Priority:** P1 (validates the core crawl logic)
- **Status:** completed
- **Description:** Plain-Mockito unit test for `SocialCrawlerService.crawlPost` — mock the
  repository, verify `save()` called once with the correct post and positive metric values.

## Key Insights
- `@ExtendWith(MockitoExtension.class)` — **no Spring context** (A4). Therefore `@Async`
  does NOT fire: `crawlPost()` runs synchronously on the test thread and returns an
  already-completed future. Call `.get()` (or `.join()`) to unwrap and surface any error.
- Mock `SocialMetricRepository`; `@InjectMocks SocialCrawlerService`.
- Capture the saved entity with `ArgumentCaptor<SocialMetric>` to assert its fields.
- Test both platforms (FACEBOOK, TWITTER) so platform-seeded ranges are exercised.

## Requirements
- Functional: R11 — `save()` called once with correct `Post` ref; positive metric values.
- Coverage: happy path per platform; assert `crawledAt` set; assert counts within/above 0.

## Architecture
```
@ExtendWith(MockitoExtension.class)
class SocialCrawlerServiceTest
  @Mock SocialMetricRepository repo
  @InjectMocks SocialCrawlerService service

  crawlPost_savesMetricForFacebookPost():
    post = FACEBOOK Post
    service.crawlPost(post).get()           // sync in test, unwrap
    verify(repo).save(captor.capture())
    assert saved.post == post
    assert all counts > 0, crawledAt != null

  crawlPost_savesMetricForTwitterPost(): same, TWITTER
```

## Related Code Files
- **Create:** `src/test/java/com/sunasterisk/socialanalytics/service/SocialCrawlerServiceTest.java`
- **Read:** `service/SocialCrawlerService.java`, `entity/{Post,SocialMetric,SocialProvider}.java`

## Implementation Steps
1. Create test class with `@ExtendWith(MockitoExtension.class)`, `@Mock` repo, `@InjectMocks` service.
2. Helper to build a `Post` via Lombok `@Builder` with a given `SocialProvider` platform
   (id, platformPostId, status ACTIVE — minimal fields).
3. `crawlPost_savesMetricForFacebookPost`:
   - `service.crawlPost(fbPost).get();`
   - `ArgumentCaptor<SocialMetric> captor`; `verify(repo).save(captor.capture());`
   - assert `saved.getPost()` == fbPost.
   - assert `likesCount/sharesCount/commentsCount/followersCount/reach/impressions` all `> 0`.
   - assert `saved.getCrawledAt() != null`.
4. `crawlPost_savesMetricForTwitterPost`: same with TWITTER post.
5. (Optional, KISS) assert FB likes ≤ 1000 / TW likes ≤ 500 to pin the range seeding —
   only if it doesn't make the test brittle.

## Todo List
- [ ] Create `SocialCrawlerServiceTest` (`@ExtendWith(MockitoExtension.class)`)
- [ ] FB post test: `save()` once, correct post, positive values, `crawledAt` set
- [ ] TWITTER post test: same
- [ ] Unwrap future with `.get()` (verifies no exception thrown)
- [ ] `mvn -q test -Dtest=SocialCrawlerServiceTest` green

## Success Criteria
- Both tests pass; `save()` verified once per call with the exact `Post` reference.
- Full `mvn -q test` stays green (no regressions).

## Risk Assessment
| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Asserting exact random values → flaky | Med | Med | Assert `> 0` / `<= max` bounds, never equality |
| `@InjectMocks` fails if ctor changes | Low | Low | Keep crawler's single-dep constructor stable |
| Forgetting `.get()` hides a thrown exception | Low | Med | Always unwrap the future in the test |

## Security Considerations
- None. Test-only; no real persistence, no credentials.

## Next Steps
- On green, D4 is functionally + test complete. Hand to reviewer; update docs/changelog.

## Rollback
- Delete the test file. No production impact.

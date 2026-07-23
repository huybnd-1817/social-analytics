package com.sunasterisk.socialanalytics.integration;

import com.sunasterisk.socialanalytics.entity.Post;
import com.sunasterisk.socialanalytics.entity.PostStatus;
import com.sunasterisk.socialanalytics.entity.SocialProvider;
import com.sunasterisk.socialanalytics.entity.User;
import com.sunasterisk.socialanalytics.repository.PostRepository;
import com.sunasterisk.socialanalytics.repository.SocialMetricRepository;
import com.sunasterisk.socialanalytics.repository.UserRepository;
import com.sunasterisk.socialanalytics.service.CrawlJobService;
import com.sunasterisk.socialanalytics.service.MetricsBroadcaster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * D6-10: integration test for the crawl job pipeline.
 *
 * Flow: CrawlJobService.updateSocialMetricsJob() → @Async SocialCrawlerService.crawlPost() per post
 *       → SocialMetricRepository.save() → MetricsBroadcaster.broadcast("CRAWL_COMPLETE")
 *
 * The job calls CompletableFuture.allOf(...).join(), so by the time the method returns
 * all @Async tasks (and their @Transactional commits) have completed.
 * No Awaitility needed — the call blocks until every metric is persisted.
 *
 * Proxy ordering invariant: @Async is the OUTER proxy, @Transactional is INNER.
 * The submitted task executes the full TX-wrapped invocation; the TX commits inside
 * proceed(), before the @Async interceptor resolves the returned CompletableFuture.
 * allOf().join() therefore unblocks only after the commit — safe to assert DB state.
 *
 * NOT @Transactional on the test — @Async tasks open their own transactions;
 * a surrounding test transaction would prevent them from seeing committed data.
 */
@SpringBootTest
@ActiveProfiles("test")
class CrawlJobIntegrationTest {

    @Autowired private CrawlJobService crawlJobService;
    @Autowired private PostRepository postRepository;
    @Autowired private SocialMetricRepository socialMetricRepository;
    @Autowired private UserRepository userRepository;
    @MockitoSpyBean private MetricsBroadcaster metricsBroadcaster;

    private static final int SEED_POST_COUNT = 2;

    @BeforeEach
    void setUp() {
        socialMetricRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        User user = userRepository.save(User.builder()
                .email("crawl-seed@test.com")
                .name("Crawl Seed User")
                .build());

        postRepository.save(Post.builder()
                .user(user)
                .platform(SocialProvider.FACEBOOK)
                .platformPostId("fb-crawl-001")
                .title("Facebook Crawl Post")
                .status(PostStatus.ACTIVE)
                .build());
        postRepository.save(Post.builder()
                .user(user)
                .platform(SocialProvider.TWITTER)
                .platformPostId("tw-crawl-001")
                .title("Twitter Crawl Post")
                .status(PostStatus.ACTIVE)
                .build());
    }

    @AfterEach
    void tearDown() {
        socialMetricRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    /**
     * TC-01: job runs → one SocialMetric row per active post saved →
     * MetricsBroadcaster.broadcast("CRAWL_COMPLETE") called exactly once.
     */
    @Test
    void crawlJob_savesOneMetricPerPostAndBroadcasts() {
        assertThat(socialMetricRepository.count()).isZero();

        crawlJobService.updateSocialMetricsJob();

        assertThat(socialMetricRepository.count()).isEqualTo(SEED_POST_COUNT);
        verify(metricsBroadcaster).broadcast("CRAWL_COMPLETE");
    }

    /**
     * TC-02: DELETED posts are skipped — only ACTIVE posts produce metrics.
     */
    @Test
    void crawlJob_skipsDeletedPosts() {
        User user = userRepository.findFirstByOrderByIdAsc().orElseThrow();
        postRepository.save(Post.builder()
                .user(user)
                .platform(SocialProvider.FACEBOOK)
                .platformPostId("fb-deleted-001")
                .title("Deleted Post")
                .status(PostStatus.DELETED)
                .build());

        crawlJobService.updateSocialMetricsJob();

        // Only the 2 ACTIVE posts should have been crawled
        assertThat(socialMetricRepository.count()).isEqualTo(SEED_POST_COUNT);
    }

    /**
     * TC-03: lastCrawledAt is set (or refreshed) after a successful run.
     * CrawlJobService is a singleton; previous tests in the same context may have
     * already set lastCrawledAt — capture before-value and verify it strictly advanced.
     */
    @Test
    void crawlJob_updatesLastCrawledAt() {
        java.time.Instant before = crawlJobService.getLastCrawledAt();

        crawlJobService.updateSocialMetricsJob();

        java.time.Instant after = crawlJobService.getLastCrawledAt();
        assertThat(after).isNotNull();
        if (before != null) {
            assertThat(after).isAfter(before);
        }
    }
}

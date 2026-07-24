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
 * D6-10: kiểm thử tích hợp cho luồng crawl job.
 *
 * Luồng: CrawlJobService.updateSocialMetricsJob() → @Async SocialCrawlerService.crawlPost() theo từng post
 *        → SocialMetricRepository.save() → MetricsBroadcaster.broadcast("CRAWL_COMPLETE")
 *
 * Job gọi CompletableFuture.allOf(...).join(), nên khi method trả về thì toàn bộ
 * @Async task (và @Transactional commit của chúng) đã hoàn tất.
 * Không cần Awaitility — lệnh gọi block cho đến khi mọi metric được lưu vào DB.
 *
 * Bất biến về thứ tự proxy: @Async là proxy NGOÀI, @Transactional là proxy TRONG.
 * Task được submit thực thi toàn bộ lời gọi có TX bọc ngoài; TX commit bên trong
 * proceed(), trước khi @Async interceptor resolve CompletableFuture được trả về.
 * allOf().join() do đó chỉ unblock sau khi commit — an toàn để assert trạng thái DB.
 *
 * Test KHÔNG dùng @Transactional — các @Async task tự mở transaction riêng;
 * nếu có transaction bao ngoài từ test, chúng sẽ không thấy dữ liệu đã committed.
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
     * TC-01: job chạy → lưu đúng một SocialMetric cho mỗi post đang active →
     * MetricsBroadcaster.broadcast("CRAWL_COMPLETE") được gọi đúng một lần.
     */
    @Test
    void crawlJob_savesOneMetricPerPostAndBroadcasts() {
        assertThat(socialMetricRepository.count()).isZero();

        crawlJobService.updateSocialMetricsJob();

        assertThat(socialMetricRepository.count()).isEqualTo(SEED_POST_COUNT);
        verify(metricsBroadcaster).broadcast("CRAWL_COMPLETE");
    }

    /**
     * TC-02: post có trạng thái DELETED bị bỏ qua — chỉ post ACTIVE mới tạo metric.
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
     * TC-03: lastCrawledAt được gán (hoặc cập nhật) sau khi job chạy thành công.
     * CrawlJobService là singleton; các test trước trong cùng context có thể đã gán
     * lastCrawledAt — lưu giá trị trước khi chạy và xác nhận nó tăng lên sau đó.
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

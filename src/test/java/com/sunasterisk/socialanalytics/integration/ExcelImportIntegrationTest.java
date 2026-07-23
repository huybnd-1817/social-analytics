package com.sunasterisk.socialanalytics.integration;

import com.sunasterisk.socialanalytics.entity.User;
import com.sunasterisk.socialanalytics.messaging.ImportStatsCache;
import com.sunasterisk.socialanalytics.repository.ImportBatchRepository;
import com.sunasterisk.socialanalytics.repository.PostRepository;
import com.sunasterisk.socialanalytics.repository.SocialMetricRepository;
import com.sunasterisk.socialanalytics.repository.UserRepository;
import com.sunasterisk.socialanalytics.service.MetricsBroadcaster;
import com.sunasterisk.socialanalytics.util.ExcelFixtureBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D6-09: end-to-end integration test for the Excel import pipeline.
 *
 * Flow: POST /import-posts → ExcelImportService (TX commits) →
 *       ImportSucceededEvent → ImportEventProducer (AFTER_COMMIT) →
 *       JMS IMPORT_COMPLETED → ImportEventListener → ImportStatsCache + MetricsBroadcaster
 *
 * NOT @Transactional on the test — TransactionalEventListener(AFTER_COMMIT)
 * only fires on a real commit; a rolled-back test transaction suppresses it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExcelImportIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ImportBatchRepository importBatchRepository;
    @Autowired private SocialMetricRepository socialMetricRepository;
    @Autowired private ImportStatsCache importStatsCache;
    @MockitoSpyBean private MetricsBroadcaster metricsBroadcaster;

    @BeforeEach
    void setUp() {
        socialMetricRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        importBatchRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        importStatsCache.reset();

        userRepository.save(User.builder()
                .email("seed@test.com")
                .name("Seed User")
                .build());
    }

    @AfterEach
    void tearDown() {
        socialMetricRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        importBatchRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    /**
     * TC-01: valid Excel → posts persisted → IMPORT_COMPLETED published →
     * ImportStatsCache updated → MetricsBroadcaster.broadcast("IMPORT_COMPLETE") called.
     */
    @Test
    void uploadValidExcel_persistsPostsAndUpdatesStats() throws Exception {
        MockMultipartFile file = ExcelFixtureBuilder.build("posts.xlsx",
                new String[]{"platform", "platform_post_id", "title"},
                new String[]{"FACEBOOK", "fb-int-001", "Integration Post 1"},
                new String[]{"TWITTER",  "tw-int-001", "Integration Post 2"});

        mockMvc.perform(multipart("/import-posts").file(file).with(user("tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.successRecords").value(2))
                .andExpect(jsonPath("$.failedRecords").value(0));

        assertThat(postRepository.count()).isEqualTo(2);

        // Wait for AFTER_COMMIT → JMS → listener → cache + broadcast
        await().atMost(5, TimeUnit.SECONDS)
               .pollInterval(200, TimeUnit.MILLISECONDS)
               .untilAsserted(() -> {
                   assertThat(importStatsCache.get()).isPresent();
                   assertThat(importStatsCache.get().get().totalPosts()).isEqualTo(2);
                   verify(metricsBroadcaster, atLeastOnce()).broadcast("IMPORT_COMPLETE");
               });
    }

    /**
     * TC-02: duplicate post (same platform + platform_post_id) already in DB →
     * BR-003 triggers → batch FAILED → no new posts saved.
     */
    @Test
    void uploadDuplicateExcel_returnsFailed_noPostsAdded() throws Exception {
        MockMultipartFile first = ExcelFixtureBuilder.build("first.xlsx",
                new String[]{"platform", "platform_post_id", "title"},
                new String[]{"FACEBOOK", "fb-dup-001", "Original Post"});
        mockMvc.perform(multipart("/import-posts").file(first).with(user("tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        long countAfterFirst = postRepository.count();

        // Wait for first import's AFTER_COMMIT → JMS → listener cycle to complete
        // before issuing the second request, so the stats assertion is deterministic.
        await().atMost(3, TimeUnit.SECONDS)
               .until(() -> importStatsCache.get().isPresent());

        MockMultipartFile second = ExcelFixtureBuilder.build("second.xlsx",
                new String[]{"platform", "platform_post_id", "title"},
                new String[]{"FACEBOOK", "fb-dup-001", "Duplicate Post"});
        mockMvc.perform(multipart("/import-posts").file(second).with(user("tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.successRecords").value(0));

        assertThat(postRepository.count()).isEqualTo(countAfterFirst);
        // FAILED import must not trigger ImportSucceededEvent → stats cache stays empty for this batch
        assertThat(importStatsCache.get().map(s -> s.totalPosts()).orElse(-1L)).isEqualTo(countAfterFirst);
    }

    /**
     * TC-03: unauthenticated request → 302 redirect to /login (not 200 or 403).
     */
    @Test
    void uploadWithoutAuth_redirectsToLogin() throws Exception {
        MockMultipartFile file = ExcelFixtureBuilder.build("posts.xlsx",
                new String[]{"platform", "platform_post_id"},
                new String[]{"FACEBOOK", "fb-noauth-001"});

        mockMvc.perform(multipart("/import-posts").file(file))
                .andExpect(status().is3xxRedirection());
    }
}

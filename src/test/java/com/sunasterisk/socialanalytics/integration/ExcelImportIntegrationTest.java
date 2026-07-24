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
 * D6-09: kiểm thử tích hợp end-to-end cho luồng import Excel.
 *
 * Luồng: POST /import-posts → ExcelImportService (TX commit) →
 *        ImportSucceededEvent → ImportEventProducer (AFTER_COMMIT) →
 *        JMS IMPORT_COMPLETED → ImportEventListener → ImportStatsCache + MetricsBroadcaster
 *
 * Test KHÔNG dùng @Transactional — TransactionalEventListener(AFTER_COMMIT)
 * chỉ được kích hoạt sau khi commit thật sự; transaction của test bị rollback sẽ chặn sự kiện này.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExcelImportIntegrationTest {

    @Autowired private MockMvc mockMvc; // ← được inject nhờ @AutoConfigureMockMvc
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
     * TC-01: Excel hợp lệ → post được lưu vào DB → IMPORT_COMPLETED được publish →
     * ImportStatsCache cập nhật → MetricsBroadcaster.broadcast("IMPORT_COMPLETE") được gọi.
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
                   assertThat(importStatsCache.get().orElseThrow().totalPosts()).isEqualTo(2);
                   verify(metricsBroadcaster, atLeastOnce()).broadcast("IMPORT_COMPLETE");
               });
    }

    /**
     * TC-02: post trùng lặp (cùng platform + platform_post_id) đã có trong DB →
     * BR-003 kích hoạt → batch FAILED → không có post mới nào được lưu.
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
        assertThat(importStatsCache.get().map(ImportStatsCache.ImportStats::totalPosts).orElse(-1L)).isEqualTo(countAfterFirst);
    }

    /**
     * TC-03: request không có xác thực → redirect 302 về /login (không phải 200 hay 403).
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

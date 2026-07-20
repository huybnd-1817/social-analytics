package com.sunasterisk.socialanalytics.controller;

import com.sunasterisk.socialanalytics.config.SecurityConfig;
import com.sunasterisk.socialanalytics.security.CustomOAuth2UserService;
import com.sunasterisk.socialanalytics.service.CrawlJobService;
import com.sunasterisk.socialanalytics.service.MetricService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MetricController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class MetricControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MetricService metricService;

    @MockitoBean
    private CrawlJobService crawlJobService;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void lastUpdated_jobNeverRan_returnsNullTimestamp() throws Exception {
        when(crawlJobService.getLastCrawledAt()).thenReturn(null);

        mockMvc.perform(get("/metrics/last-updated").with(user("testuser")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastCrawledAt").doesNotExist());
    }

    @Test
    void lastUpdated_afterJobRan_returnsIsoTimestamp() throws Exception {
        Instant ts = Instant.parse("2026-07-15T07:00:01Z");
        when(crawlJobService.getLastCrawledAt()).thenReturn(ts);

        mockMvc.perform(get("/metrics/last-updated").with(user("testuser")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastCrawledAt").value("2026-07-15T07:00:01Z"));
    }

    @Test
    void lastUpdated_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/metrics/last-updated"))
                .andExpect(status().is3xxRedirection());
    }
}

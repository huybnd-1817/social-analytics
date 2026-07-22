package com.sunasterisk.socialanalytics.service;

import com.sunasterisk.socialanalytics.dto.ChartDataResponse;
import com.sunasterisk.socialanalytics.entity.Post;
import com.sunasterisk.socialanalytics.entity.PostStatus;
import com.sunasterisk.socialanalytics.entity.SocialMetric;
import com.sunasterisk.socialanalytics.entity.SocialProvider;
import com.sunasterisk.socialanalytics.repository.SocialMetricRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChartDataServiceTest {

    @Mock
    private SocialMetricRepository socialMetricRepository;

    @InjectMocks
    private ChartDataService chartDataService;

    private SocialMetric metric(SocialProvider platform, Instant crawledAt, long likes, long shares) {
        Post post = Post.builder()
                .platform(platform)
                .platformPostId("pid")
                .status(PostStatus.ACTIVE)
                .build();
        return SocialMetric.builder()
                .post(post)
                .crawledAt(crawledAt)
                .likesCount(likes)
                .sharesCount(shares)
                .build();
    }

    @Test
    void noFilters_callsFindAllWithPost() {
        Instant day1 = Instant.parse("2026-07-01T12:00:00Z");
        when(socialMetricRepository.findAllWithPost()).thenReturn(List.of(
                metric(SocialProvider.FACEBOOK, day1, 100L, 10L),
                metric(SocialProvider.TWITTER,  day1,  50L,  5L)
        ));

        ChartDataResponse result = chartDataService.getChartData(null, null, null);

        assertThat(result.labels()).containsExactly("2026-07-01");
        assertThat(result.datasets()).hasSize(2);
        assertThat(result.datasets().get(0).platform()).isEqualTo("FACEBOOK");
        assertThat(result.datasets().get(1).platform()).isEqualTo("TWITTER");
        verify(socialMetricRepository).findAllWithPost();
    }

    @Test
    void platformFilter_callsFindByPlatform() {
        Instant day1 = Instant.parse("2026-07-01T08:00:00Z");
        when(socialMetricRepository.findByPlatform("FACEBOOK"))
                .thenReturn(List.of(metric(SocialProvider.FACEBOOK, day1, 200L, 20L)));

        ChartDataResponse result = chartDataService.getChartData("FACEBOOK", null, null);

        assertThat(result.datasets()).hasSize(1);
        assertThat(result.datasets().get(0).platform()).isEqualTo("FACEBOOK");
        verify(socialMetricRepository).findByPlatform("FACEBOOK");
    }

    @Test
    void platformFilter_caseInsensitive() {
        when(socialMetricRepository.findByPlatform("TWITTER")).thenReturn(List.of());

        chartDataService.getChartData("twitter", null, null);

        verify(socialMetricRepository).findByPlatform("TWITTER");
    }

    @Test
    void dateRangeFilter_callsFindByDateRange() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to   = Instant.parse("2026-07-07T23:59:59Z");
        when(socialMetricRepository.findByDateRange(from, to)).thenReturn(List.of());

        chartDataService.getChartData(null, from, to);

        verify(socialMetricRepository).findByDateRange(from, to);
    }

    @Test
    void platformAndDateRange_callsFindByPlatformAndDateRange() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to   = Instant.parse("2026-07-07T23:59:59Z");
        when(socialMetricRepository.findByPlatformAndDateRange("FACEBOOK", from, to)).thenReturn(List.of());

        chartDataService.getChartData("FACEBOOK", from, to);

        verify(socialMetricRepository).findByPlatformAndDateRange("FACEBOOK", from, to);
    }

    @Test
    void aggregation_sumsMultipleMetricsSameDay() {
        Instant morning = Instant.parse("2026-07-01T08:00:00Z");
        Instant evening = Instant.parse("2026-07-01T20:00:00Z");
        when(socialMetricRepository.findByPlatform("FACEBOOK")).thenReturn(List.of(
                metric(SocialProvider.FACEBOOK, morning, 100L, 10L),
                metric(SocialProvider.FACEBOOK, evening,  50L,  5L)
        ));

        ChartDataResponse result = chartDataService.getChartData("FACEBOOK", null, null);

        assertThat(result.labels()).containsExactly("2026-07-01");
        ChartDataResponse.DatasetEntry fb = result.datasets().get(0);
        assertThat(fb.likes()).containsExactly(150L);
        assertThat(fb.shares()).containsExactly(15L);
    }

    @Test
    void aggregation_labelsOrderedChronologically() {
        Instant day1 = Instant.parse("2026-07-01T10:00:00Z");
        Instant day2 = Instant.parse("2026-07-03T10:00:00Z");
        Instant day3 = Instant.parse("2026-07-02T10:00:00Z"); // intentionally out of order
        when(socialMetricRepository.findAllWithPost()).thenReturn(List.of(
                metric(SocialProvider.FACEBOOK, day1, 10L, 1L),
                metric(SocialProvider.FACEBOOK, day2, 30L, 3L),
                metric(SocialProvider.FACEBOOK, day3, 20L, 2L)
        ));

        ChartDataResponse result = chartDataService.getChartData(null, null, null);

        assertThat(result.labels()).containsExactly("2026-07-01", "2026-07-02", "2026-07-03");
    }

    @Test
    void missingPlatformOnADay_fillsZero() {
        Instant day1 = Instant.parse("2026-07-01T10:00:00Z");
        Instant day2 = Instant.parse("2026-07-02T10:00:00Z");
        when(socialMetricRepository.findAllWithPost()).thenReturn(List.of(
                metric(SocialProvider.FACEBOOK, day1, 100L, 10L),
                metric(SocialProvider.FACEBOOK, day2,  80L,  8L),
                metric(SocialProvider.TWITTER,  day2,  40L,  4L)
        ));

        ChartDataResponse result = chartDataService.getChartData(null, null, null);

        assertThat(result.labels()).containsExactly("2026-07-01", "2026-07-02");
        ChartDataResponse.DatasetEntry twitter = result.datasets().stream()
                .filter(d -> "TWITTER".equals(d.platform()))
                .findFirst().orElseThrow();
        assertThat(twitter.likes()).containsExactly(0L, 40L);
        assertThat(twitter.shares()).containsExactly(0L, 4L);
    }

    @Test
    void emptyMetrics_returnsEmptyLabelsAndDatasets() {
        when(socialMetricRepository.findAllWithPost()).thenReturn(List.of());

        ChartDataResponse result = chartDataService.getChartData(null, null, null);

        assertThat(result.labels()).isEmpty();
        assertThat(result.datasets()).isEmpty();
    }

    @Test
    void invalidPlatform_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> chartDataService.getChartData("INSTAGRAM", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(socialMetricRepository);
    }
}

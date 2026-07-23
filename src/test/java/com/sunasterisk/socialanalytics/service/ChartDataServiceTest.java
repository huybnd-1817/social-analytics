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

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

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
    void noFilters_defaultsTo30DayWindow() {
        when(socialMetricRepository.findByDateRange(any(), any())).thenReturn(List.of());

        Instant before = Instant.now();
        chartDataService.getChartData(null, null, null, ZoneId.of("UTC"));
        Instant after = Instant.now();

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor   = ArgumentCaptor.forClass(Instant.class);
        verify(socialMetricRepository).findByDateRange(fromCaptor.capture(), toCaptor.capture());

        Instant capturedFrom = fromCaptor.getValue();
        Instant capturedTo   = toCaptor.getValue();
        Duration window = Duration.between(capturedFrom, capturedTo);

        assertThat(capturedTo).isBetween(before, after);
        assertThat(window.toDays()).isEqualTo(30);
    }

    @Test
    void platformFilter_defaultsTo30DayWindow() {
        Instant day1 = Instant.parse("2026-07-01T08:00:00Z");
        when(socialMetricRepository.findByPlatformAndDateRange(eq("FACEBOOK"), any(), any()))
                .thenReturn(List.of(metric(SocialProvider.FACEBOOK, day1, 200L, 20L)));

        ChartDataResponse result = chartDataService.getChartData("FACEBOOK", null, null, ZoneId.of("UTC"));

        assertThat(result.datasets()).hasSize(1);
        assertThat(result.datasets().get(0).platform()).isEqualTo("FACEBOOK");
        verify(socialMetricRepository).findByPlatformAndDateRange(eq("FACEBOOK"), any(), any());
    }

    @Test
    void platformFilter_caseInsensitive() {
        when(socialMetricRepository.findByPlatformAndDateRange(eq("TWITTER"), any(), any()))
                .thenReturn(List.of());

        chartDataService.getChartData("twitter", null, null, ZoneId.of("UTC"));

        verify(socialMetricRepository).findByPlatformAndDateRange(eq("TWITTER"), any(), any());
    }

    @Test
    void dateRangeFilter_callsFindByDateRange() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to   = Instant.parse("2026-07-07T23:59:59Z");
        when(socialMetricRepository.findByDateRange(from, to)).thenReturn(List.of());

        chartDataService.getChartData(null, from, to, ZoneId.of("UTC"));

        verify(socialMetricRepository).findByDateRange(from, to);
    }

    @Test
    void platformAndDateRange_callsFindByPlatformAndDateRange() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to   = Instant.parse("2026-07-07T23:59:59Z");
        when(socialMetricRepository.findByPlatformAndDateRange("FACEBOOK", from, to)).thenReturn(List.of());

        chartDataService.getChartData("FACEBOOK", from, to, ZoneId.of("UTC"));

        verify(socialMetricRepository).findByPlatformAndDateRange("FACEBOOK", from, to);
    }

    @Test
    void aggregation_sumsMultipleMetricsSameDay() {
        Instant morning = Instant.parse("2026-07-01T08:00:00Z");
        Instant evening = Instant.parse("2026-07-01T20:00:00Z");
        when(socialMetricRepository.findByPlatformAndDateRange(eq("FACEBOOK"), any(), any())).thenReturn(List.of(
                metric(SocialProvider.FACEBOOK, morning, 100L, 10L),
                metric(SocialProvider.FACEBOOK, evening,  50L,  5L)
        ));

        ChartDataResponse result = chartDataService.getChartData("FACEBOOK", null, null, ZoneId.of("UTC"));

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
        when(socialMetricRepository.findByDateRange(any(), any())).thenReturn(List.of(
                metric(SocialProvider.FACEBOOK, day1, 10L, 1L),
                metric(SocialProvider.FACEBOOK, day2, 30L, 3L),
                metric(SocialProvider.FACEBOOK, day3, 20L, 2L)
        ));

        ChartDataResponse result = chartDataService.getChartData(null, null, null, ZoneId.of("UTC"));

        assertThat(result.labels()).containsExactly("2026-07-01", "2026-07-02", "2026-07-03");
    }

    @Test
    void missingPlatformOnADay_fillsZero() {
        Instant day1 = Instant.parse("2026-07-01T10:00:00Z");
        Instant day2 = Instant.parse("2026-07-02T10:00:00Z");
        when(socialMetricRepository.findByDateRange(any(), any())).thenReturn(List.of(
                metric(SocialProvider.FACEBOOK, day1, 100L, 10L),
                metric(SocialProvider.FACEBOOK, day2,  80L,  8L),
                metric(SocialProvider.TWITTER,  day2,  40L,  4L)
        ));

        ChartDataResponse result = chartDataService.getChartData(null, null, null, ZoneId.of("UTC"));

        assertThat(result.labels()).containsExactly("2026-07-01", "2026-07-02");
        ChartDataResponse.DatasetEntry twitter = result.datasets().stream()
                .filter(d -> "TWITTER".equals(d.platform()))
                .findFirst().orElseThrow();
        assertThat(twitter.likes()).containsExactly(0L, 40L);
        assertThat(twitter.shares()).containsExactly(0L, 4L);
    }

    @Test
    void emptyMetrics_returnsEmptyLabelsAndDatasets() {
        when(socialMetricRepository.findByDateRange(any(), any())).thenReturn(List.of());

        ChartDataResponse result = chartDataService.getChartData(null, null, null, ZoneId.of("UTC"));

        assertThat(result.labels()).isEmpty();
        assertThat(result.datasets()).isEmpty();
    }

    @Test
    void explicitRange_over90Days_throwsIllegalArgumentException() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to   = from.plus(Duration.ofDays(91));
        assertThatThrownBy(() -> chartDataService.getChartData(null, from, to, ZoneId.of("UTC")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("90");
        verifyNoInteractions(socialMetricRepository);
    }

    @Test
    void explicitRange_exactly90Days_isAccepted() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to   = from.plus(Duration.ofDays(90));
        when(socialMetricRepository.findByDateRange(from, to)).thenReturn(List.of());

        chartDataService.getChartData(null, from, to, ZoneId.of("UTC"));

        verify(socialMetricRepository).findByDateRange(from, to);
    }

    @Test
    void fromOnly_throwsIllegalArgumentException() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        assertThatThrownBy(() -> chartDataService.getChartData(null, from, null, ZoneId.of("UTC")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Both 'from' and 'to'");
        verifyNoInteractions(socialMetricRepository);
    }

    @Test
    void toOnly_throwsIllegalArgumentException() {
        Instant to = Instant.parse("2026-07-07T23:59:59Z");
        assertThatThrownBy(() -> chartDataService.getChartData(null, null, to, ZoneId.of("UTC")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Both 'from' and 'to'");
        verifyNoInteractions(socialMetricRepository);
    }

    @Test
    void invalidPlatform_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> chartDataService.getChartData("INSTAGRAM", null, null, ZoneId.of("UTC")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(socialMetricRepository);
    }

    @Test
    void timezone_shiftsDayBoundary() {
        // 2026-07-01T23:00:00Z = 2026-07-02T06:00:00+07:00 (Asia/Ho_Chi_Minh)
        Instant lateUtc = Instant.parse("2026-07-01T23:00:00Z");
        when(socialMetricRepository.findByDateRange(any(), any())).thenReturn(List.of(
                metric(SocialProvider.FACEBOOK, lateUtc, 100L, 10L)
        ));

        // UTC → label 2026-07-01
        ChartDataResponse utcResult = chartDataService.getChartData(null, null, null, ZoneId.of("UTC"));
        assertThat(utcResult.labels()).containsExactly("2026-07-01");

        // GMT+7 → same Instant belongs to 2026-07-02
        when(socialMetricRepository.findByDateRange(any(), any())).thenReturn(List.of(
                metric(SocialProvider.FACEBOOK, lateUtc, 100L, 10L)
        ));
        ChartDataResponse vn = chartDataService.getChartData(null, null, null, ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(vn.labels()).containsExactly("2026-07-02");
    }
}

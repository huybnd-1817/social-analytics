package com.sunasterisk.socialanalytics.service;

import com.sunasterisk.socialanalytics.dto.MetricResponse;
import com.sunasterisk.socialanalytics.entity.Post;
import com.sunasterisk.socialanalytics.entity.SocialMetric;
import com.sunasterisk.socialanalytics.repository.SocialMetricRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricServiceTest {

    @Mock
    private SocialMetricRepository socialMetricRepository;

    @InjectMocks
    private MetricService metricService;

    @Test
    void findAll_returnsMappedPage() {
        // MetricResponse.from đọc metric.getPost().getId() — fixture bắt buộc có Post kèm id
        Post post = Post.builder().id(7L).build();
        Instant crawledAt = Instant.parse("2026-07-09T10:00:00Z");
        SocialMetric metric = SocialMetric.builder()
                .id(1L)
                .post(post)
                .likesCount(100L)
                .sharesCount(20L)
                .commentsCount(5L)
                .followersCount(1000L)
                .reach(5000L)
                .impressions(8000L)
                .crawledAt(crawledAt)
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        when(socialMetricRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(metric), pageable, 1));

        Page<MetricResponse> result = metricService.findAll(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        MetricResponse mapped = result.getContent().get(0);
        assertThat(mapped.id()).isEqualTo(1L);
        assertThat(mapped.postId()).isEqualTo(7L);
        assertThat(mapped.likesCount()).isEqualTo(100L);
        assertThat(mapped.sharesCount()).isEqualTo(20L);
        assertThat(mapped.commentsCount()).isEqualTo(5L);
        assertThat(mapped.followersCount()).isEqualTo(1000L);
        assertThat(mapped.reach()).isEqualTo(5000L);
        assertThat(mapped.impressions()).isEqualTo(8000L);
        assertThat(mapped.crawledAt()).isEqualTo(crawledAt);
        // @CreatedDate không được auditing listener set trong unit test — null là chủ đích
        assertThat(mapped.createdAt()).isNull();
        verify(socialMetricRepository).findAll(pageable);
    }

    @Test
    void findAll_mapsZeroCountDefaults() {
        // Metric mới build không set count nào — @Builder.Default phải cho 0, không NPE
        SocialMetric metric = SocialMetric.builder()
                .id(2L)
                .post(Post.builder().id(8L).build())
                .crawledAt(Instant.parse("2026-07-09T11:00:00Z"))
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        when(socialMetricRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(metric), pageable, 1));

        MetricResponse mapped = metricService.findAll(pageable).getContent().get(0);

        assertThat(mapped.likesCount()).isZero();
        assertThat(mapped.sharesCount()).isZero();
        assertThat(mapped.commentsCount()).isZero();
        assertThat(mapped.followersCount()).isZero();
        assertThat(mapped.reach()).isZero();
        assertThat(mapped.impressions()).isZero();
    }
}

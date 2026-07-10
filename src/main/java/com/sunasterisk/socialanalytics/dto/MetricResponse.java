package com.sunasterisk.socialanalytics.dto;

import com.sunasterisk.socialanalytics.entity.SocialMetric;

import java.time.Instant;

public record MetricResponse(
        Long id,
        Long postId,
        Long likesCount,
        Long sharesCount,
        Long commentsCount,
        Long followersCount,
        Long reach,
        Long impressions,
        Instant crawledAt,
        Instant createdAt
) {
    public static MetricResponse from(SocialMetric metric) {
        return new MetricResponse(
                metric.getId(),
                metric.getPost().getId(),
                metric.getLikesCount(),
                metric.getSharesCount(),
                metric.getCommentsCount(),
                metric.getFollowersCount(),
                metric.getReach(),
                metric.getImpressions(),
                metric.getCrawledAt(),
                metric.getCreatedAt()
        );
    }
}

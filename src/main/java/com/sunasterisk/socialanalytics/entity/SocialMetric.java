package com.sunasterisk.socialanalytics.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "social_metrics")
@EntityListeners(AuditingEntityListener.class)
public class SocialMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Builder.Default
    @Column(name = "likes_count", nullable = false)
    private Long likesCount = 0L;

    @Builder.Default
    @Column(name = "shares_count", nullable = false)
    private Long sharesCount = 0L;

    @Builder.Default
    @Column(name = "comments_count", nullable = false)
    private Long commentsCount = 0L;

    @Builder.Default
    @Column(name = "followers_count", nullable = false)
    private Long followersCount = 0L;

    @Builder.Default
    @Column(name = "reach", nullable = false)
    private Long reach = 0L;

    @Builder.Default
    @Column(name = "impressions", nullable = false)
    private Long impressions = 0L;

    @Column(name = "crawled_at", nullable = false)
    private Instant crawledAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

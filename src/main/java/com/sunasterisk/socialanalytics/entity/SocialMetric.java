package com.sunasterisk.socialanalytics.entity;

import com.sunasterisk.socialanalytics.util.ExcelColumn;
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

    @ExcelColumn(headerName = "likes_count", order = 1)
    @Builder.Default
    @Column(name = "likes_count", nullable = false)
    private Long likesCount = 0L;

    @ExcelColumn(headerName = "shares_count", order = 2)
    @Builder.Default
    @Column(name = "shares_count", nullable = false)
    private Long sharesCount = 0L;

    @ExcelColumn(headerName = "comments_count", order = 3)
    @Builder.Default
    @Column(name = "comments_count", nullable = false)
    private Long commentsCount = 0L;

    @ExcelColumn(headerName = "followers_count", order = 4)
    @Builder.Default
    @Column(name = "followers_count", nullable = false)
    private Long followersCount = 0L;

    @ExcelColumn(headerName = "reach", order = 5)
    @Builder.Default
    @Column(name = "reach", nullable = false)
    private Long reach = 0L;

    @ExcelColumn(headerName = "impressions", order = 6)
    @Builder.Default
    @Column(name = "impressions", nullable = false)
    private Long impressions = 0L;

    @ExcelColumn(headerName = "crawled_at", order = 7, format = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "crawled_at", nullable = false)
    private Instant crawledAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

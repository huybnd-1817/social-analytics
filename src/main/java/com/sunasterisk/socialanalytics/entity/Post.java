package com.sunasterisk.socialanalytics.entity;

import com.sunasterisk.socialanalytics.util.ExcelColumn;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "posts")
public class Post extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ExcelColumn(headerName = "platform", order = 1)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "platform", nullable = false)
    private SocialProvider platform;

    @ExcelColumn(headerName = "platform_post_id", order = 2)
    @Column(name = "platform_post_id", nullable = false, length = 255)
    private String platformPostId;

    @ExcelColumn(headerName = "title", order = 3)
    @Column(name = "title", length = 500)
    private String title;

    // Không xuất ra Excel — trường TEXT không giới hạn độ dài; xem ExportRowModel để biết tập cột xuất
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @ExcelColumn(headerName = "post_url", order = 4)
    @Column(name = "post_url", columnDefinition = "TEXT")
    private String postUrl;

    @ExcelColumn(headerName = "published_at", order = 5, format = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "published_at")
    private Instant publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    private ImportBatch importBatch;

    @ExcelColumn(headerName = "status", order = 6)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    @Column(name = "status", nullable = false)
    private PostStatus status = PostStatus.ACTIVE;
}

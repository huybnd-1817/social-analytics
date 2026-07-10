package com.sunasterisk.socialanalytics.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "import_batches")
@EntityListeners(AuditingEntityListener.class)
public class ImportBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Builder.Default
    @Column(name = "total_records", nullable = false)
    private Integer totalRecords = 0;

    @Builder.Default
    @Column(name = "success_records", nullable = false)
    private Integer successRecords = 0;

    @Builder.Default
    @Column(name = "failed_records", nullable = false)
    private Integer failedRecords = 0;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    @Column(name = "status", nullable = false)
    private ImportBatchStatus status = ImportBatchStatus.PENDING;

    // @CreatedDate: gán lúc persist (batch bắt đầu) để INSERT không vi phạm NOT NULL;
    // service Day-2 sẽ set lại tường minh khi import hoàn tất.
    @CreatedDate
    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;
}

package com.sunasterisk.socialanalytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Base class cho tất cả entity có created_at / updated_at.
 * Dùng Instant (không phải LocalDateTime) vì cột DB là TIMESTAMPTZ:
 * Instant không phụ thuộc timezone của JVM — LocalDateTime sẽ ghi giờ
 * wall-clock địa phương bị gắn nhãn UTC, lệch giờ so với trigger của DB.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

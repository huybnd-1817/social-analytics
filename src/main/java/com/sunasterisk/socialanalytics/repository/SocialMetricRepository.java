package com.sunasterisk.socialanalytics.repository;

import com.sunasterisk.socialanalytics.entity.Post;
import com.sunasterisk.socialanalytics.entity.SocialMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SocialMetricRepository extends JpaRepository<SocialMetric, Long> {
    // Lấy bản ghi metric mới nhất của một bài post dựa theo thời điểm crawl (crawledAt) giảm dần
    Optional<SocialMetric> findTop1ByPostOrderByCrawledAtDesc(Post post);

    // 4 query riêng — tránh nullable parameter: PostgreSQL không infer được type
    // cho bất kỳ kiểu nào (enum, timestamptz) khi giá trị là null trong prepared statement.

    @Query("SELECT m FROM SocialMetric m JOIN FETCH m.post ORDER BY m.crawledAt ASC")
    List<SocialMetric> findAllWithPost();

    @Query("SELECT m FROM SocialMetric m JOIN FETCH m.post p " +
           "WHERE CAST(p.platform AS string) = :platformStr ORDER BY m.crawledAt ASC")
    List<SocialMetric> findByPlatform(@Param("platformStr") String platformStr);

    @Query("SELECT m FROM SocialMetric m JOIN FETCH m.post " +
           "WHERE m.crawledAt >= :from AND m.crawledAt <= :to ORDER BY m.crawledAt ASC")
    List<SocialMetric> findByDateRange(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT m FROM SocialMetric m JOIN FETCH m.post p " +
           "WHERE CAST(p.platform AS string) = :platformStr " +
           "AND m.crawledAt >= :from AND m.crawledAt <= :to ORDER BY m.crawledAt ASC")
    List<SocialMetric> findByPlatformAndDateRange(
            @Param("platformStr") String platformStr,
            @Param("from") Instant from,
            @Param("to") Instant to);
}

package com.sunasterisk.socialanalytics.repository;

import com.sunasterisk.socialanalytics.entity.Post;
import com.sunasterisk.socialanalytics.entity.SocialMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SocialMetricRepository extends JpaRepository<SocialMetric, Long> {
    // Lấy bản ghi metric mới nhất của một bài post dựa theo thời điểm crawl (crawledAt) giảm dần
    Optional<SocialMetric> findTop1ByPostOrderByCrawledAtDesc(Post post);
}

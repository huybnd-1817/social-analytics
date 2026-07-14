package com.sunasterisk.socialanalytics.repository;

import com.sunasterisk.socialanalytics.entity.Post;
import com.sunasterisk.socialanalytics.entity.PostStatus;
import com.sunasterisk.socialanalytics.entity.SocialProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    Page<Post> findByStatus(PostStatus status, Pageable pageable);

    Optional<Post> findByIdAndStatus(Long id, PostStatus status);

    // Export report: lấy toàn bộ post ACTIVE không phân trang (quy mô demo — chấp nhận unbounded)
    List<Post> findByStatus(PostStatus status);

    // Import: dò trùng khóa (platform, platform_post_id) đang ACTIVE theo lô,
    // khớp partial unique index của bảng posts
    List<Post> findByStatusAndPlatformAndPlatformPostIdIn(
            PostStatus status, SocialProvider platform, Collection<String> platformPostIds);
}

package com.sunasterisk.socialanalytics.repository;

import com.sunasterisk.socialanalytics.config.JpaAuditingConfig;
import com.sunasterisk.socialanalytics.entity.Post;
import com.sunasterisk.socialanalytics.entity.PostStatus;
import com.sunasterisk.socialanalytics.entity.SocialProvider;
import com.sunasterisk.socialanalytics.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice test chạy trên H2 (profile "test" tắt Flyway — V1__init_schema.sql là DDL Postgres-only,
 * Hibernate create-drop tự sinh schema từ entity). Import JpaAuditingConfig vì slice không load
 * main config → thiếu @EnableJpaAuditing thì created_at NOT NULL sẽ fail khi insert.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class PostRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PostRepository postRepository;

    private User user;
    private Post activePost;
    private Post deletedPost;

    @BeforeEach
    void setUp() {
        // Post.user_id NOT NULL — phải persist User thật trước
        user = em.persist(User.builder()
                .email("tester@example.com")
                .name("Tester")
                .build());
        activePost = em.persist(post("fb-active", PostStatus.ACTIVE));
        deletedPost = em.persist(post("fb-deleted", PostStatus.DELETED));
        em.flush();
    }

    private Post post(String platformPostId, PostStatus status) {
        // content/postUrl/publishedAt nullable — bỏ qua có chủ đích, không ảnh hưởng query
        return Post.builder()
                .user(user)
                .platform(SocialProvider.FACEBOOK)
                .platformPostId(platformPostId)
                .title("Title " + platformPostId)
                .status(status)
                .build();
    }

    @Test
    void findByStatus_returnsOnlyActivePosts() {
        Page<Post> result = postRepository.findByStatus(PostStatus.ACTIVE, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(activePost.getId());
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(PostStatus.ACTIVE);
    }

    @Test
    void findByIdAndStatus_returnsPost_whenStatusMatches() {
        Optional<Post> found = postRepository.findByIdAndStatus(activePost.getId(), PostStatus.ACTIVE);

        assertThat(found).isPresent();
        assertThat(found.get().getPlatformPostId()).isEqualTo("fb-active");
    }

    @Test
    void findByIdAndStatus_returnsEmpty_whenStatusMismatch() {
        // Post tồn tại nhưng đã DELETED — truy vấn ACTIVE phải rỗng (soft-delete vô hình)
        Optional<Post> found = postRepository.findByIdAndStatus(deletedPost.getId(), PostStatus.ACTIVE);

        assertThat(found).isEmpty();
    }

    @Test
    void findByIdAndStatus_returnsEmpty_whenIdAbsent() {
        Optional<Post> found = postRepository.findByIdAndStatus(999_999L, PostStatus.ACTIVE);

        assertThat(found).isEmpty();
    }

    @Test
    void softDelete_persistsStatusChange() {
        // Chứng minh dirty-check thật sự ghi xuống DB (PostService.deleteById không gọi save())
        activePost.setStatus(PostStatus.DELETED);
        em.flush();
        em.clear();

        assertThat(postRepository.findByIdAndStatus(activePost.getId(), PostStatus.ACTIVE)).isEmpty();
        assertThat(em.find(Post.class, activePost.getId()).getStatus()).isEqualTo(PostStatus.DELETED);
    }

    @Test
    void findByStatus_pagination_runsCountQuery() {
        // 2 ACTIVE + page size 1 → Spring Data buộc phải chạy count query (page đầy)
        em.persist(post("fb-active-2", PostStatus.ACTIVE));
        em.flush();

        Page<Post> page = postRepository.findByStatus(PostStatus.ACTIVE, PageRequest.of(0, 1));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }
}

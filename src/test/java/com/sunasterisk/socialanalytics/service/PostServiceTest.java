package com.sunasterisk.socialanalytics.service;

import com.sunasterisk.socialanalytics.dto.PostResponse;
import com.sunasterisk.socialanalytics.entity.Post;
import com.sunasterisk.socialanalytics.entity.PostStatus;
import com.sunasterisk.socialanalytics.entity.SocialProvider;
import com.sunasterisk.socialanalytics.repository.PostRepository;
import com.sunasterisk.socialanalytics.util.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Kích hoạt Mockito cho class test này
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private PostService postService;

    private Post activePost(Long id) {
        return Post.builder()
                .id(id)
                .platform(SocialProvider.FACEBOOK)
                .platformPostId("fb-" + id)
                .title("Title " + id)
                .content("Content " + id)
                .postUrl("https://facebook.com/posts/" + id)
                .publishedAt(Instant.parse("2026-07-08T09:00:00Z"))
                .build(); // status mặc định ACTIVE qua @Builder.Default
    }

    @Test
    void findAll_returnsActivePosts_paged() {
        Pageable pageable = PageRequest.of(0, 20);
        List<Post> posts = List.of(activePost(1L), activePost(2L));
        when(postRepository.findByStatus(PostStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(posts, pageable, 2));

        Page<PostResponse> result = postService.findAll(pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(PostResponse::id, PostResponse::platform, PostResponse::platformPostId, PostResponse::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, "FACEBOOK", "fb-1", "ACTIVE"),
                        org.assertj.core.groups.Tuple.tuple(2L, "FACEBOOK", "fb-2", "ACTIVE"));
        // Khóa toàn bộ mapping PostResponse.from — swap field nào cũng phải bị bắt ở đây
        PostResponse first = result.getContent().get(0);
        assertThat(first.title()).isEqualTo("Title 1");
        assertThat(first.content()).isEqualTo("Content 1");
        assertThat(first.postUrl()).isEqualTo("https://facebook.com/posts/1");
        assertThat(first.publishedAt()).isEqualTo(Instant.parse("2026-07-08T09:00:00Z"));
        assertThat(first.createdAt()).isNull(); // @CreatedDate không chạy ngoài Spring context
        verify(postRepository).findByStatus(PostStatus.ACTIVE, pageable);
    }

    @Test
    void deleteById_softDeletes_activePost() {
        Post post = activePost(1L);
        when(postRepository.findByIdAndStatus(1L, PostStatus.ACTIVE)).thenReturn(Optional.of(post));

        postService.deleteById(1L);

        assertThat(post.getStatus()).isEqualTo(PostStatus.DELETED);
        // Soft delete dựa vào @Transactional dirty-check — không được gọi save() tường minh.
        // Bản thân dirty-check (flush khi commit) được PostRepositoryTest (@DataJpaTest) chứng minh;
        // unit test này chỉ khóa contract "không save() thủ công".
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void deleteById_throwsResourceNotFoundException_whenNotFound() {
        when(postRepository.findByIdAndStatus(99L, PostStatus.ACTIVE)).thenReturn(Optional.empty());

        ResourceNotFoundException ex =
                assertThrows(ResourceNotFoundException.class, () -> postService.deleteById(99L));

        assertThat(ex.getMessage()).isEqualTo("Post not found: 99");
        verify(postRepository, never()).save(any(Post.class));
    }
}

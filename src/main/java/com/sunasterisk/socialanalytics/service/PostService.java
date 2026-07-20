package com.sunasterisk.socialanalytics.service;

import com.sunasterisk.socialanalytics.dto.PostResponse;
import com.sunasterisk.socialanalytics.entity.Post;
import com.sunasterisk.socialanalytics.entity.PostStatus;
import com.sunasterisk.socialanalytics.repository.PostRepository;
import com.sunasterisk.socialanalytics.util.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;

    @Transactional(readOnly = true)
    public Page<PostResponse> findAll(Pageable pageable) {
        return postRepository.findByStatus(PostStatus.ACTIVE, pageable)
                .map(PostResponse::from);
    }

    // Soft delete: chỉ đổi status sang DELETED thay vì xóa khỏi DB.
    // Không cần gọi repository.save() vì @Transactional giữ entity ở trạng thái managed —
    // JPA tự động phát hiện thay đổi (dirty checking) và sinh câu UPDATE khi transaction commit.
    @Transactional
    public void deleteById(Long id) {
        Post post = postRepository.findByIdAndStatus(id, PostStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + id));
        post.setStatus(PostStatus.DELETED);
    }
}

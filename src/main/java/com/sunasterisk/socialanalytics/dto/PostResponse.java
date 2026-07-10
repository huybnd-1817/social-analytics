package com.sunasterisk.socialanalytics.dto;

import com.sunasterisk.socialanalytics.entity.Post;

import java.time.Instant;

public record PostResponse(
        Long id,
        String platform,
        String platformPostId,
        String title,
        String content,
        String postUrl,
        Instant publishedAt,
        String status,
        Instant createdAt
) {
    public static PostResponse from(Post post) {
        return new PostResponse(
                post.getId(),
                post.getPlatform().name(),
                post.getPlatformPostId(),
                post.getTitle(),
                post.getContent(),
                post.getPostUrl(),
                post.getPublishedAt(),
                post.getStatus().name(),
                post.getCreatedAt()
        );
    }
}

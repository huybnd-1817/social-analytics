package com.sunasterisk.socialanalytics.service;

import com.sunasterisk.socialanalytics.entity.Post;
import com.sunasterisk.socialanalytics.entity.PostStatus;
import com.sunasterisk.socialanalytics.entity.SocialProvider;
import com.sunasterisk.socialanalytics.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrawlJobServiceTest {

    @Mock
    private SocialCrawlerService socialCrawlerService;

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private CrawlJobService crawlJobService;

    private Post post(long id) {
        return Post.builder()
                .platform(SocialProvider.FACEBOOK)
                .platformPostId("pid-" + id)
                .status(PostStatus.ACTIVE)
                .build();
    }

    @Test
    void job_withActivePosts_callsCrawlForEachAndSetsLastCrawledAt() {
        List<Post> posts = List.of(post(1), post(2), post(3));
        when(postRepository.findByStatus(PostStatus.ACTIVE)).thenReturn(posts);
        when(socialCrawlerService.crawlPost(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        crawlJobService.updateSocialMetricsJob();

        verify(socialCrawlerService, times(3)).crawlPost(any());
        assertThat(crawlJobService.getLastCrawledAt()).isNotNull();
    }

    @Test
    void job_noPosts_doesNotSetLastCrawledAt() {
        when(postRepository.findByStatus(PostStatus.ACTIVE)).thenReturn(List.of());

        crawlJobService.updateSocialMetricsJob();

        verify(socialCrawlerService, never()).crawlPost(any());
        // success = 0, so lastCrawledAt stays null — "Never crawled" is accurate
        assertThat(crawlJobService.getLastCrawledAt()).isNull();
    }

    @Test
    void job_crawlThrows_doesNotSetLastCrawledAt() {
        Post p = post(1);
        when(postRepository.findByStatus(PostStatus.ACTIVE)).thenReturn(List.of(p));
        when(socialCrawlerService.crawlPost(p))
                .thenThrow(new RuntimeException("mock API down"));

        crawlJobService.updateSocialMetricsJob();

        // all tasks failed (success = 0), so lastCrawledAt stays null — no data was written
        assertThat(crawlJobService.getLastCrawledAt()).isNull();
    }

    @Test
    void getLastCrawledAt_beforeFirstRun_returnsNull() {
        assertThat(crawlJobService.getLastCrawledAt()).isNull();
    }
}

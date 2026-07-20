package com.sunasterisk.socialanalytics.service;

import com.sunasterisk.socialanalytics.entity.Post;
import com.sunasterisk.socialanalytics.entity.PostStatus;
import com.sunasterisk.socialanalytics.entity.SocialMetric;
import com.sunasterisk.socialanalytics.entity.SocialProvider;
import com.sunasterisk.socialanalytics.repository.SocialMetricRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SocialCrawlerServiceTest {

    @Mock
    private SocialMetricRepository socialMetricRepository;

    @InjectMocks
    private SocialCrawlerService socialCrawlerService;

    private Post buildPost(SocialProvider platform) {
        return Post.builder()
                .platform(platform)
                .platformPostId("test-id-" + platform)
                .status(PostStatus.ACTIVE)
                .build();
    }

    @Test
    void crawlPost_facebook_savesMetricWithPositiveValues() throws ExecutionException, InterruptedException {
        Post post = buildPost(SocialProvider.FACEBOOK);
        when(socialMetricRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompletableFuture<Void> future = socialCrawlerService.crawlPost(post);
        future.get(); // @Async doesn't fire in plain Mockito — runs synchronously

        ArgumentCaptor<SocialMetric> captor = ArgumentCaptor.forClass(SocialMetric.class);
        verify(socialMetricRepository, times(1)).save(captor.capture());

        SocialMetric saved = captor.getValue();
        assertThat(saved.getPost()).isSameAs(post);
        assertThat(saved.getLikesCount()).isBetween(100L, 1000L);
        assertThat(saved.getSharesCount()).isBetween(10L, 200L);
        assertThat(saved.getCommentsCount()).isBetween(1L, 50L);
        assertThat(saved.getFollowersCount()).isBetween(200L, 5000L);
        assertThat(saved.getReach()).isBetween(500L, 10000L);
        assertThat(saved.getImpressions()).isBetween(1000L, 50000L);
        assertThat(saved.getCrawledAt()).isNotNull();
    }

    @Test
    void crawlPost_twitter_savesMetricWithTwitterRanges() throws ExecutionException, InterruptedException {
        Post post = buildPost(SocialProvider.TWITTER);
        when(socialMetricRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompletableFuture<Void> future = socialCrawlerService.crawlPost(post);
        future.get();

        ArgumentCaptor<SocialMetric> captor = ArgumentCaptor.forClass(SocialMetric.class);
        verify(socialMetricRepository).save(captor.capture());

        SocialMetric saved = captor.getValue();
        assertThat(saved.getLikesCount()).isBetween(50L, 500L);
        assertThat(saved.getSharesCount()).isBetween(5L, 100L);
    }

    @Test
    void crawlPost_returnsCompletedFuture() throws ExecutionException, InterruptedException {
        Post post = buildPost(SocialProvider.FACEBOOK);
        when(socialMetricRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompletableFuture<Void> future = socialCrawlerService.crawlPost(post);

        assertThat(future).isNotNull();
        assertThat(future.isDone()).isTrue();
        assertThat(future.isCompletedExceptionally()).isFalse();
        future.get(); // must not throw
    }
}

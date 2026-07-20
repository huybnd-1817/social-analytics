package com.sunasterisk.socialanalytics.service;

import com.sunasterisk.socialanalytics.entity.Post;
import com.sunasterisk.socialanalytics.entity.SocialMetric;
import com.sunasterisk.socialanalytics.entity.SocialProvider;
import com.sunasterisk.socialanalytics.repository.SocialMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bean tách biệt khỏi CrawlJobService — @Async proxy của Spring bị bỏ qua khi
 * tự gọi trong cùng một bean (self-invocation), nên crawler phải nằm ở bean riêng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialCrawlerService {

    // Repository lưu metrics crawl được vào database
    private final SocialMetricRepository socialMetricRepository;

    // Chạy bất đồng bộ trên thread pool của AsyncConfig — không chặn thread gọi
    @Async
    // Đảm bảo toàn bộ method nằm trong một transaction: nếu save thất bại, không có dữ liệu nào bị ghi nửa chừng
    @Transactional
    public CompletableFuture<Void> crawlPost(Post post) {
        // ThreadLocalRandom an toàn và hiệu quả hơn Random trong môi trường đa luồng
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Sinh số liệu giả theo từng nền tảng — Facebook thường có lượt tương tác cao hơn Twitter
        long likes;
        long shares;
        if (post.getPlatform() == SocialProvider.FACEBOOK) {
            likes  = rng.nextLong(100, 1001);  // 100–1000 lượt thích
            shares = rng.nextLong(10, 201);    // 10–200 lượt chia sẻ
        } else {
            // TWITTER
            likes  = rng.nextLong(50, 501);    // 50–500 lượt thích
            shares = rng.nextLong(5, 101);     // 5–100 lượt retweet
        }

        // Các chỉ số chung cho cả hai nền tảng
        long comments    = rng.nextLong(1, 51);       // 1–50 bình luận
        long followers   = rng.nextLong(200, 5001);   // 200–5000 người theo dõi tài khoản
        long reach       = rng.nextLong(500, 10001);  // 500–10000 người thực sự thấy bài
        long impressions = rng.nextLong(1000, 50001); // 1000–50000 lần hiển thị (kể cả xem nhiều lần)

        // Tạo bản ghi metric và gắn với post tương ứng
        SocialMetric metric = SocialMetric.builder()
                .post(post)
                .likesCount(likes)
                .sharesCount(shares)
                .commentsCount(comments)
                .followersCount(followers)
                .reach(reach)
                .impressions(impressions)
                .crawledAt(Instant.now())
                .build();

        socialMetricRepository.save(metric);

        log.debug("crawled post id={} platform={} likes={} shares={}",
                post.getId(), post.getPlatform(), likes, shares);

        // Trả về CompletableFuture<Void> đã hoàn thành — caller có thể theo dõi trạng thái
        // hoặc tổng hợp nhiều future song song nếu cần
        return CompletableFuture.completedFuture(null);
    }
}

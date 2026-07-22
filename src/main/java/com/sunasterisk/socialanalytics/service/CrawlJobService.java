package com.sunasterisk.socialanalytics.service;

import com.sunasterisk.socialanalytics.entity.Post;
import com.sunasterisk.socialanalytics.entity.PostStatus;
import com.sunasterisk.socialanalytics.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlJobService {

    // Thực hiện crawl từng post bất đồng bộ trên thread pool riêng
    private final SocialCrawlerService socialCrawlerService;
    // Truy vấn danh sách post cần crawl từ database
    private final PostRepository postRepository;
    // Broadcast metrics realtime đến client qua WebSocket sau mỗi lần crawl
    private final MetricsBroadcaster metricsBroadcaster;
    // AtomicReference đảm bảo đọc/ghi lastCrawledAt an toàn giữa nhiều thread mà không cần synchronized
    private final AtomicReference<Instant> lastCrawledAt = new AtomicReference<>();

    // fixedDelay: lần chạy tiếp theo chỉ bắt đầu SAU KHI lần trước hoàn thành — tránh hai job crawl chạy đồng thời và ghi đè dữ liệu nhau
    @Scheduled(fixedDelayString = "${app.crawler.rate-ms:3600000}")
    public void updateSocialMetricsJob() {
        Instant start = Instant.now();

        // Chỉ crawl các post đang ACTIVE, bỏ qua post đã bị xóa hoặc tạm dừng
        List<Post> posts = postRepository.findByStatus(PostStatus.ACTIVE);
        log.info("crawl job started: {} active posts", posts.size());

        // Gửi từng post vào thread pool bất đồng bộ, thu thập future để theo dõi kết quả
        List<CompletableFuture<Void>> futures = posts.stream()
                .map(this::safeCrawl)
                .toList();

        // allOf().join() chờ TẤT CẢ future hoàn thành và ném CompletionException nếu có bất kỳ future nào lỗi.
        // Dùng try/catch để finally luôn chạy — đảm bảo lastCrawledAt được cập nhật đúng trạng thái.
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception ex) {
            log.warn("one or more crawl tasks failed: {}", ex.getMessage());
        } finally {
            // Đếm số task thất bại để log báo cáo — không throw, job vẫn kết thúc bình thường
            long failed = futures.stream().filter(CompletableFuture::isCompletedExceptionally).count();
            long success = futures.size() - failed;
            long elapsedMs = Duration.between(start, Instant.now()).toMillis();
            log.info("crawl job done: success={} failed={} elapsed={}ms", success, failed, elapsedMs);
            // Chỉ cập nhật lastCrawledAt khi có ít nhất một task thành công —
            // tránh hiển thị "vừa cập nhật" trên dashboard khi không có dữ liệu nào được ghi
            if (success > 0) {
                lastCrawledAt.set(Instant.now());
                metricsBroadcaster.broadcast("CRAWL_COMPLETE");
            }
        }
    }

    // Trả về thời điểm crawl thành công gần nhất — dùng để hiển thị trên dashboard
    public Instant getLastCrawledAt() {
        return lastCrawledAt.get();
    }

    // Bọc crawlPost trong try/catch để một post lỗi không làm hỏng toàn bộ job.
    // Trả về failedFuture thay vì throw để allOf() vẫn tổng hợp được tất cả kết quả.
    private CompletableFuture<Void> safeCrawl(Post post) {
        try {
            return socialCrawlerService.crawlPost(post);
        } catch (Exception ex) {
            log.error("failed to dispatch crawl for post id={}: {}", post.getId(), ex.getMessage(), ex);
            return CompletableFuture.failedFuture(ex);
        }
    }
}

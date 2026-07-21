package com.sunasterisk.socialanalytics.messaging;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Cache in-memory lưu thống kê post tổng hợp mới nhất, được cập nhật sau mỗi lần import. */
@Component
public class ImportStatsCache {

    public record ImportStats(long totalPosts, Map<String, Long> perPlatform) {}

    private final AtomicReference<ImportStats> latest = new AtomicReference<>();

    public void update(ImportStats stats) {
        latest.set(stats);
    }

    /** Trả về snapshot thống kê mới nhất, empty nếu chưa có import nào hoàn thành. */
    public Optional<ImportStats> get() {
        return Optional.ofNullable(latest.get());
    }

    /** Xóa cache về trạng thái ban đầu — chỉ dùng trong test. */
    void reset() {
        latest.set(null);
    }
}

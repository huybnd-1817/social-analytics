package com.sunasterisk.socialanalytics.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @EnableAsync / @EnableScheduling đặt tại đây thay vì class ứng dụng chính để
 * @WebMvcTest / @DataJpaTest slice không vô tình kích hoạt async wiring.
 * @EnableAsync - Kích hoạt hỗ trợ bất đồng bộ: các method được đánh dấu @Async sẽ chạy trên thread pool riêng,
 * không chặn thread gọi.
 * @EnableScheduling - Kích hoạt lập lịch tác vụ: các method được đánh dấu @Scheduled sẽ được Spring
 * tự động gọi theo cron expression hoặc khoảng thời gian cố định.
 */
@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    // Số thread luôn sẵn sàng trong pool, kể cả khi không có task nào đang chạy.
    @Value("${app.async.core-pool-size:5}")
    private int corePoolSize;

    // Số thread tối đa pool có thể tạo khi queue đã đầy.
    @Value("${app.async.max-pool-size:10}")
    private int maxPoolSize;

    // Số task được xếp hàng chờ khi tất cả core thread đang bận.
    // Khi queue đầy và pool chưa đạt maxPoolSize, Spring mới tạo thêm thread.
    @Value("${app.async.queue-capacity:100}")
    private int queueCapacity;

    // Tiền tố tên thread — giúp phân biệt các thread trong pool được tạo ra
    // để chạy các tác vụ crawl dữ liệu (fetch trang web, lấy metrics từ mạng xã hội...) trong log và trong thread dump.
    @Value("${app.async.thread-name-prefix:crawler-}")
    private String threadNamePrefix;

    /**
     * Cấu hình thread pool dùng cho tất cả method được đánh dấu @Async.
     * Spring sẽ dùng executor này thay vì SimpleAsyncTaskExecutor mặc định.
     */
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        // Dòng này cấu hình chiến lược xử lý khi thread pool bị quá tải — tức là khi cả 10 thread đang bận VÀ queue 100 task đã đầy.
        // CallerRunsPolicy có nghĩa: thread nào gọi .execute() thì thread đó tự chạy task luôn — không ném exception, không bỏ task.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Bắt exception ném ra từ các method @Async (vì caller không nhận được exception bất đồng bộ).
     * Log đầy đủ method name, tham số, và stack trace để dễ debug.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
            log.error("[ASYNC-ERROR] method={} args={} ex={}",
                method.getName(), Arrays.toString(params), ex.getMessage(), ex);
    }
}

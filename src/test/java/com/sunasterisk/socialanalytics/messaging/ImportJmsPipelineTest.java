package com.sunasterisk.socialanalytics.messaging;

import jakarta.jms.ConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test cho JMS import pipeline sử dụng embedded Artemis broker.
 * Không mock — chạy full Spring context với H2 in-memory + Artemis in-VM.
 */
@SpringBootTest
@ActiveProfiles("test")
class ImportJmsPipelineTest {

    @Autowired private JmsTemplate jmsTemplate;
    @Autowired private ImportStatsCache importStatsCache;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ConnectionFactory connectionFactory;

    @BeforeEach
    void resetCache() {
        importStatsCache.reset();
    }

    /**
     * TC-01: gửi message trực tiếp vào queue IMPORT_COMPLETED →
     * @JmsListener xử lý → ImportStatsCache được cập nhật.
     */
    @Test
    void sendToQueue_listenerRecalculatesStats() {
        jmsTemplate.convertAndSend(JmsQueues.IMPORT_COMPLETED,
                new ImportCompletedMessage(42L, 5));

        await().atMost(5, TimeUnit.SECONDS)
               .pollInterval(100, TimeUnit.MILLISECONDS)
               .until(() -> importStatsCache.get().isPresent());

        assertThat(importStatsCache.get()).isPresent();
        assertThat(importStatsCache.get().get().totalPosts()).isGreaterThanOrEqualTo(0);
    }

    /**
     * TC-02: ImportSucceededEvent được publish trong transaction sắp rollback →
     * @TransactionalEventListener(AFTER_COMMIT) KHÔNG kích hoạt → không có JMS message nào được gửi
     * → stats cache vẫn rỗng.
     */
    @Test
    void rollbackTransaction_doesNotPublishJmsMessage() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            eventPublisher.publishEvent(new ImportSucceededEvent(999L, 0));
            status.setRollbackOnly();   // ép rollback — AFTER_COMMIT không được chạy
            return null;
        });

        // Cache đã reset trong @BeforeEach; chờ một khoảng — nếu AFTER_COMMIT vô tình chạy, cache sẽ có giá trị
        await().during(400, TimeUnit.MILLISECONDS)
               .atMost(600, TimeUnit.MILLISECONDS)
               .until(() -> importStatsCache.get().isEmpty());

        assertThat(importStatsCache.get()).isEmpty();
    }

    /**
     * TC-03: queue DLQ.IMPORT_COMPLETED có thể truy cập và giữ lại message.
     *
     * <p>Phạm vi: gửi trực tiếp vào DLQ và đọc lại, xác nhận queue được tự tạo
     * và message được lưu giữ. Kiểm tra hạ tầng DLQ (Artemis auto-create-queues).
     *
     * <p>Ngoài phạm vi: luồng retry-rồi-DLQ là built-in của Artemis (không phải custom code)
     * nên không kiểm tra ở đây. Luồng DLQ thực tế (listener throw → NACK → hết retry →
     * Artemis chuyển sang DLQ) được đảm bảo bởi try/catch + re-throw trong ImportEventListener,
     * nhưng để test end-to-end cần broker.xml với max-delivery-attempts ngắn —
     * để lại như bước xác nhận thủ công.
     */
    @Test
    void dlqQueue_isAccessibleAndRetainsMessages() {
        String dlqName = "DLQ." + JmsQueues.IMPORT_COMPLETED;

        // Artemis tự tạo queue DLQ khi dùng lần đầu (auto-create-queues=true theo mặc định)
        jmsTemplate.convertAndSend(dlqName, new ImportCompletedMessage(0L, 0));

        JmsTemplate receiver = new JmsTemplate(connectionFactory);
        receiver.setReceiveTimeout(3000L);

        await().atMost(5, TimeUnit.SECONDS)
               .pollInterval(200, TimeUnit.MILLISECONDS)
               .until(() -> receiver.receive(dlqName) != null);
    }
}

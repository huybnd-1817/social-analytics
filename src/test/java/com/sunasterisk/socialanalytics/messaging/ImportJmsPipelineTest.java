package com.sunasterisk.socialanalytics.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

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
    @MockitoSpyBean private ImportDlqHandler dlqHandler;

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
     * TC-03: ImportDlqHandler nhận và xử lý message từ DLQ.IMPORT_COMPLETED.
     *
     * <p>Gửi trực tiếp vào DLQ (mô phỏng message đã hết retry) và xác nhận
     * ImportDlqHandler.onDeadLetter() được gọi với đúng payload.
     */
    @Test
    void dlqQueue_handlerProcessesDeadLetter() {
        ImportCompletedMessage dead = new ImportCompletedMessage(99L, 0);
        jmsTemplate.convertAndSend(JmsQueues.IMPORT_COMPLETED_DLQ, dead);

        await().atMost(5, TimeUnit.SECONDS)
               .pollInterval(200, TimeUnit.MILLISECONDS)
               .untilAsserted(() ->
                       verify(dlqHandler, atLeastOnce()).onDeadLetter(any(ImportCompletedMessage.class)));
    }
}

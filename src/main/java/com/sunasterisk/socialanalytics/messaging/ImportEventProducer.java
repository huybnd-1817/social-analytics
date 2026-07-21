package com.sunasterisk.socialanalytics.messaging;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cầu nối từ ImportSucceededEvent nội bộ sang queue JMS IMPORT_COMPLETED.
 * Chỉ chạy SAU KHI transaction DB commit thành công — tránh gửi message khi rollback.
 */
@Component
@RequiredArgsConstructor
public class ImportEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ImportEventProducer.class);

    private final JmsTemplate jmsTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onImportSucceeded(ImportSucceededEvent event) {
        ImportCompletedMessage message = new ImportCompletedMessage(event.batchId(), event.recordCount());
        try {
            jmsTemplate.convertAndSend(JmsQueues.IMPORT_COMPLETED, message);
            log.info("Published to {}: batchId={}, recordCount={}",
                    JmsQueues.IMPORT_COMPLETED, event.batchId(), event.recordCount());
        } catch (Exception ex) {
            // Import đã commit thành công — chỉ log lỗi, không propagate để tránh rollback
            log.error("Failed to publish ImportCompletedMessage for batchId={}: {}",
                    event.batchId(), ex.getMessage(), ex);
        }
    }
}

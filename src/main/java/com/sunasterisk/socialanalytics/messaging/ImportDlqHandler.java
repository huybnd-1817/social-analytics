package com.sunasterisk.socialanalytics.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * Xử lý message chết (dead letter) từ DLQ.IMPORT_COMPLETED.
 * Message vào đây sau khi ImportEventListener thất bại đủ số lần retry cho phép.
 */
@Component
public class ImportDlqHandler {

    private static final Logger log = LoggerFactory.getLogger(ImportDlqHandler.class);

    /**
     * Nhận message từ DLQ sau khi hết retry.
     * Hiện tại: log lỗi để operator biết cần điều tra thủ công.
     * Mở rộng: gửi alert, cập nhật trạng thái batch về DEAD, ghi vào bảng audit...
     */
    @JmsListener(destination = JmsQueues.IMPORT_COMPLETED_DLQ)
    public void onDeadLetter(ImportCompletedMessage message) {
        log.error("Dead-letter: batchId={}, recordCount={} — đã vượt quá số lần retry tối đa. "
                + "Cần kiểm tra ImportEventListener và trạng thái DB thủ công.",
                message.batchId(), message.recordCount());
    }
}

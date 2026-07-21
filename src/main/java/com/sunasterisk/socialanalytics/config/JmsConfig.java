package com.sunasterisk.socialanalytics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.support.converter.JacksonJsonMessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

/**
 * Cấu hình JMS: chuyển đổi message sang JSON (TextMessage) thay vì
 * Java serialization (ObjectMessage) để payload dễ đọc và debug hơn.
 * DLQ (Dead Letter Queue) được Artemis embedded tự xử lý — message nào
 * thất bại hết số lần retry sẽ tự động được chuyển sang DLQ.IMPORT_COMPLETED,
 * không cần cấu hình thêm.
 */
@Configuration
public class JmsConfig {

    @Bean
    public MessageConverter jmsMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        // Gửi message dưới dạng TextMessage (JSON string) thay vì ObjectMessage (binary)
        converter.setTargetType(MessageType.TEXT);
        // Header "_type" chứa tên class đầy đủ, giúp Jackson deserialize về đúng kiểu record khi nhận
        converter.setTypeIdPropertyName("_type");
        return converter;
    }
}

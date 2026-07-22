package com.sunasterisk.socialanalytics.config;

import com.sunasterisk.socialanalytics.messaging.JmsQueues;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.springframework.boot.artemis.autoconfigure.ArtemisConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.support.converter.JacksonJsonMessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

/**
 * Cấu hình JMS: converter JSON + address settings cho IMPORT_COMPLETED queue.
 *
 * <p>Embedded Artemis không đọc broker.xml như standalone server — ArtemisConfigurationCustomizer
 * là cách Spring Boot cấu hình address settings (max-delivery-attempts, redelivery-delay, DLQ).
 */
@Configuration
public class JmsConfig {

    @Bean
    public MessageConverter jmsMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        // TextMessage (JSON) thay vì ObjectMessage (binary) — dễ đọc và debug
        converter.setTargetType(MessageType.TEXT);
        // Header "_type" giúp Jackson deserialize về đúng record class khi nhận
        converter.setTypeIdPropertyName("_type");
        return converter;
    }

    /**
     * Address settings tương đương broker.xml cho embedded Artemis:
     * - max-delivery-attempts=3  → thử tối đa 3 lần trước khi đưa vào DLQ
     * - redelivery-delay=2000ms  → chờ 2 giây giữa mỗi lần retry
     * - dead-letter-address      → DLQ.IMPORT_COMPLETED sau khi hết lần thử
     */
    @Bean
    public ArtemisConfigurationCustomizer importQueueAddressSettings() {
        return config -> {
            AddressSettings settings = new AddressSettings();
            settings.setMaxDeliveryAttempts(3);
            settings.setRedeliveryDelay(2000L);
            settings.setDeadLetterAddress(SimpleString.of(JmsQueues.IMPORT_COMPLETED_DLQ));
            settings.setAutoCreateDeadLetterResources(true);
            config.addAddressSetting(JmsQueues.IMPORT_COMPLETED, settings);
        };
    }
}

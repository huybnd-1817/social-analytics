package com.sunasterisk.socialanalytics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP qua WebSocket thuần.
 * Hỗ trợ SockJS phía server đã bị loại bỏ trong Spring 7; client dùng WebSocket thuần trực tiếp.
 * @EnableWebSocketMessageBroker - Kích hoạt WebSocket với message broker, cho phép dùng giao thức STOMP để gửi/nhận message qua WebSocket
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Đăng ký endpoint WebSocket mà client kết nối vào (/ws)
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    // Cấu hình message broker: /topic để broker phân phối message đến client,
    // /app để định tuyến message từ client đến các @MessageMapping handler
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}

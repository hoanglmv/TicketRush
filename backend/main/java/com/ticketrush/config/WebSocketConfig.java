package com.ticketrush.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Cấu hình Message Broker (bưu điện) để xử lý tin nhắn WebSocket.
     * - Kích hoạt simple broker cho "/topic" để gửi tin nhắn một chiều từ Server đến nhiều Client (Broadcast).
     * - Đặt tiền tố "/app" cho các tin nhắn từ Client gửi lên Server.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Đăng ký endpoint kết nối ban đầu cho WebSocket.
     * - Endpoint "/ws": Đường dẫn để Client gọi kết nối lúc đầu.
     * - Cho phép mọi Origin (*) và sử dụng SockJS làm giải pháp dự phòng (fallback).
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}

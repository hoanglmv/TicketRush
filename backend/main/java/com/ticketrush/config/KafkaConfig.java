package com.ticketrush.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Cấu hình Apache Kafka cho TicketRush:
 * - Tự động tạo các Topics với số partition tối ưu cho concurrency.
 * - Cho phép bật/tắt linh hoạt qua property: app.kafka.enabled
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    @Value("${app.kafka.topics.booking:ticket-booking-events}")
    private String bookingTopic;

    @Value("${app.kafka.topics.payment:ticket-payment-events}")
    private String paymentTopic;

    @Value("${app.kafka.topics.notification:ticket-notification-events}")
    private String notificationTopic;

    @Value("${app.kafka.topics.telemetry:agent-telemetry-events}")
    private String telemetryTopic;

    /**
     * Topic đặt vé: Chia 3 partitions theo eventId để đảm bảo thứ tự (FIFO) cho từng sự kiện
     * đồng thời phân tán tải cho nhiều consumers song song.
     */
    @Bean
    public NewTopic bookingTopic() {
        return TopicBuilder.name(bookingTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Topic thanh toán vé & xuất mã QR.
     */
    @Bean
    public NewTopic paymentTopic() {
        return TopicBuilder.name(paymentTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Topic gửi thông báo và email vé điện tử (bất đồng bộ).
     */
    @Bean
    public NewTopic notificationTopic() {
        return TopicBuilder.name(notificationTopic)
                .partitions(2)
                .replicas(1)
                .build();
    }

    /**
     * Topic truyền phát nhật ký đánh giá AI Agent & RAG metrics.
     */
    @Bean
    public NewTopic telemetryTopic() {
        return TopicBuilder.name(telemetryTopic)
                .partitions(2)
                .replicas(1)
                .build();
    }
}

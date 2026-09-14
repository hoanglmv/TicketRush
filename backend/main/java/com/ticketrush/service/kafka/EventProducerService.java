package com.ticketrush.service.kafka;

import com.ticketrush.event.AgentTelemetryEvent;
import com.ticketrush.event.BookingEvent;
import com.ticketrush.event.NotificationEvent;
import com.ticketrush.event.PaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service trung tâm phát các sự kiện (Producer) lên Kafka Cluster:
 * - Hỗ trợ phân vùng partition theo key (ví dụ: eventId cho booking để đảm bảo thứ tự xử lý).
 * - Tự động kiểm tra tính sẵn sàng của Kafka (Graceful Degradation nếu Kafka tắt).
 */
@Slf4j
@Service
public class EventProducerService {

    @Autowired(required = false)
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.enabled:true}")
    private boolean kafkaEnabled;

    @Value("${app.kafka.topics.booking:ticket-booking-events}")
    private String bookingTopic;

    @Value("${app.kafka.topics.payment:ticket-payment-events}")
    private String paymentTopic;

    @Value("${app.kafka.topics.notification:ticket-notification-events}")
    private String notificationTopic;

    @Value("${app.kafka.topics.telemetry:agent-telemetry-events}")
    private String telemetryTopic;

    public void sendBookingEvent(BookingEvent event) {
        if (!isKafkaActive()) {
            log.debug("Kafka is disabled or template not available. Skipping booking event produce.");
            return;
        }
        // Dùng eventId làm message key để tất cả booking của cùng 1 event rơi vào cùng 1 partition (bảo toàn FIFO)
        String partitionKey = event.getEventId() != null ? String.valueOf(event.getEventId()) : "default";
        kafkaTemplate.send(bookingTopic, partitionKey, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Kafka produced BookingEvent for seatId={} to partition={}",
                                event.getSeatId(), result.getRecordMetadata().partition());
                    } else {
                        log.error("Failed to produce BookingEvent: {}", ex.getMessage());
                    }
                });
    }

    public void sendPaymentEvent(PaymentEvent event) {
        if (!isKafkaActive()) return;
        String key = String.valueOf(event.getTicketId());
        kafkaTemplate.send(paymentTopic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Kafka produced PaymentEvent for ticketId={}", event.getTicketId());
                    } else {
                        log.error("Failed to produce PaymentEvent: {}", ex.getMessage());
                    }
                });
    }

    public void sendNotificationEvent(NotificationEvent event) {
        if (!isKafkaActive()) return;
        String key = event.getRecipientEmail() != null ? event.getRecipientEmail() : "notification";
        kafkaTemplate.send(notificationTopic, key, event);
    }

    public void sendAgentTelemetry(AgentTelemetryEvent event) {
        if (!isKafkaActive()) return;
        kafkaTemplate.send(telemetryTopic, event.getLogId(), event);
    }

    public boolean isKafkaActive() {
        return kafkaEnabled && kafkaTemplate != null;
    }
}

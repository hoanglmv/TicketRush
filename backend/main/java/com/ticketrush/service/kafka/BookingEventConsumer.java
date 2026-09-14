package com.ticketrush.service.kafka;

import com.ticketrush.event.BookingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Consumer tiếp nhận các sự kiện đặt vé từ Kafka:
 * - Tiếp nhận message tuần tự từ từng partition theo eventId.
 * - Giảm thiểu áp lực lock trực tiếp vào Database (Rate Limiting & Backpressure).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class BookingEventConsumer {

    @KafkaListener(
            topics = "${app.kafka.topics.booking:ticket-booking-events}",
            groupId = "ticketrush-booking-group"
    )
    public void handleBookingEvent(
            @Payload BookingEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("Kafka Consumed BookingEvent: seatId={}, userId={}, eventId={} [partition={}, offset={}]",
                event.getSeatId(), event.getUserId(), event.getEventId(), partition, offset);

        // Luồng xử lý phân tán:
        // Đã nhận được yêu cầu hợp lệ trong hàng đợi Kafka có thứ tự.
        // Có thể mở rộng để cập nhật trạng thái Redis cache hoặc thông báo WebSocket xác nhận cho Client.
    }
}

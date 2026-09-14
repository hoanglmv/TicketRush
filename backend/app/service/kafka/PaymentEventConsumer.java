package app.service.kafka;

import app.event.NotificationEvent;
import app.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Consumer xử lý các sự kiện thanh toán thành công:
 * - Tiếp nhận event thanh toán, ghi nhận doanh thu.
 * - Tự động phát ra một NotificationEvent để Worker gửi email và QR code vé.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentEventConsumer {

    private final EventProducerService eventProducerService;

    @KafkaListener(
            topics = "${app.kafka.topics.payment:ticket-payment-events}",
            groupId = "ticketrush-payment-group"
    )
    public void handlePaymentEvent(@Payload PaymentEvent event) {
        log.info("Kafka Consumed PaymentEvent: ticketId={}, amount={}, userEmail={}",
                event.getTicketId(), event.getAmount(), event.getUserEmail());

        if (event.getUserEmail() != null && !event.getUserEmail().isBlank()) {
            NotificationEvent notification = NotificationEvent.builder()
                    .type("TICKET_CONFIRMATION")
                    .recipientEmail(event.getUserEmail())
                    .eventTitle("TicketRush Concert")
                    .seatLabel(event.getSeatLabel())
                    .timestamp(LocalDateTime.now())
                    .build();

            eventProducerService.sendNotificationEvent(notification);
            log.info("Forwarded to notification topic for recipient: {}", event.getUserEmail());
        }
    }
}

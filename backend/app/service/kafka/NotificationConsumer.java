package app.service.kafka;

import app.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Consumer chuyên biệt xử lý gửi email / thông báo cho khách hàng:
 * - Đảm bảo tác vụ gửi email SMTP (thường mất 1-3 giây) không làm nghẽn tiến trình checkout của người dùng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationConsumer {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @KafkaListener(
            topics = "${app.kafka.topics.notification:ticket-notification-events}",
            groupId = "ticketrush-notification-group"
    )
    public void handleNotification(@Payload NotificationEvent event) {
        log.info("Kafka Consumed NotificationEvent: recipient={}, eventTitle={}",
                event.getRecipientEmail(), event.getEventTitle());

        try {
            if (mailSender != null && event.getRecipientEmail() != null && !event.getRecipientEmail().isBlank()) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(event.getRecipientEmail());
                message.setSubject("TicketRush: Xác nhận đặt vé thành công!");
                message.setText(String.format(
                        "Xin chào,\n\nChúc mừng bạn đã đặt vé thành công cho sự kiện: %s!\nVị trí ghế: %s.\n\nCảm ơn bạn đã tin dùng TicketRush!",
                        event.getEventTitle(),
                        event.getSeatLabel() != null ? event.getSeatLabel() : "Standard"
                ));
                mailSender.send(message);
                log.info("Ticket confirmation email sent successfully to {}", event.getRecipientEmail());
            } else {
                log.info("Simulated email notification sent to {} for event {}",
                        event.getRecipientEmail(), event.getEventTitle());
            }
        } catch (Exception e) {
            log.error("Failed to send notification email: {}", e.getMessage());
        }
    }
}

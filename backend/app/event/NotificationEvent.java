package app.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private String type;            // TICKET_CONFIRMATION, PAYMENT_REMINDER
    private String recipientEmail;
    private String recipientName;
    private String eventTitle;
    private String seatLabel;
    private String qrCodeBase64;
    private LocalDateTime timestamp;
}

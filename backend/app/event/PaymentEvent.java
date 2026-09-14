package app.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {
    private String eventType;       // PAYMENT_SUCCESS, PAYMENT_CANCELLED
    private Long ticketId;
    private Long userId;
    private Long eventId;
    private String seatLabel;
    private BigDecimal amount;
    private String userEmail;
    private String correlationId;
    private LocalDateTime timestamp;
}

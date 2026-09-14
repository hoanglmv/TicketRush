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
public class BookingEvent {
    private String eventType;       // LOCK_SEAT, RELEASE_SEAT
    private Long userId;
    private Long seatId;
    private Long eventId;
    private String correlationId;
    private LocalDateTime timestamp;
}

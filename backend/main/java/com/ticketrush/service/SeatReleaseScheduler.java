package com.ticketrush.service;

import com.ticketrush.dto.SeatStatusUpdate;
import com.ticketrush.entity.Event;
import com.ticketrush.entity.Seat;
import com.ticketrush.entity.Ticket;
import com.ticketrush.enums.SeatStatus;
import com.ticketrush.enums.TicketStatus;
import com.ticketrush.repository.EventRepository;
import com.ticketrush.repository.SeatRepository;
import com.ticketrush.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatReleaseScheduler {

    private final SeatRepository seatRepository;
    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final VirtualQueueService virtualQueueService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.seat-lock-timeout-minutes:10}")
    private int seatLockTimeoutMinutes;

    /**
     * Tác vụ chạy ngầm (Background job): Tự động giải phóng ghế đã khóa quá 10 phút mỗi 30 giây.
     * Chạy tự động không cần người dùng gọi API.
     */
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    @Transactional
    public void releaseExpiredSeats() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(seatLockTimeoutMinutes);
        List<Seat> expiredSeats = seatRepository.findByStatusAndLockedAtBefore(SeatStatus.LOCKED, cutoff);

        if (expiredSeats.isEmpty()) return;

        log.info("Releasing {} expired seat locks", expiredSeats.size());

        for (Seat seat : expiredSeats) {
            // Release seat
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setLockedAt(null);
            seat.setLockedByUserId(null);
            seatRepository.save(seat);

            // Expire related ticket
            ticketRepository.findBySeatAndStatus(seat, TicketStatus.PENDING_PAYMENT)
                    .ifPresent(ticket -> {
                        ticket.setStatus(TicketStatus.EXPIRED);
                        ticketRepository.save(ticket);
                        log.info("Expired ticket {} for seat {}", ticket.getId(), seat.getLabel());
                    });

            // Broadcast release via WebSocket
            Long eventId = seat.getZone().getEvent().getId();
            SeatStatusUpdate update = SeatStatusUpdate.builder()
                    .seatId(seat.getId())
                    .label(seat.getLabel())
                    .zoneId(seat.getZone().getId())
                    .status(SeatStatus.AVAILABLE)
                    .timestamp(LocalDateTime.now().toString())
                    .build();
            messagingTemplate.convertAndSend("/topic/event/" + eventId + "/seats", update);
        }
    }

    /**
     * Tác vụ chạy ngầm: Xử lý hàng đợi ảo (Virtual Queue) mỗi 3 giây.
     * Kiểm tra các sự kiện đang bán vé, nếu có chỗ trống sẽ cho những người đang xếp hàng vào mua.
     */
    @Scheduled(fixedRate = 3000) // Every 3 seconds
    public void processQueues() {
        List<Event> queuedEvents = eventRepository.findOnSaleWithQueueEnabled();
        for (Event event : queuedEvents) {
            virtualQueueService.processQueue(event.getId(), event.getQueueBatchSize());
        }
    }
}

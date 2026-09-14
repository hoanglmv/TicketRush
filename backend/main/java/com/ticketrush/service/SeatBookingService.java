package com.ticketrush.service;

import com.ticketrush.dto.SeatStatusUpdate;
import com.ticketrush.dto.TicketResponse;
import com.ticketrush.entity.Event;
import com.ticketrush.entity.Seat;
import com.ticketrush.entity.Ticket;
import com.ticketrush.entity.User;
import com.ticketrush.enums.EventStatus;
import com.ticketrush.enums.SeatStatus;
import com.ticketrush.enums.TicketStatus;
import com.ticketrush.exception.*;
import com.ticketrush.repository.SeatRepository;
import com.ticketrush.repository.TicketRepository;
import com.ticketrush.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatBookingService {

    private final SeatRepository seatRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final QRCodeService qrCodeService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Khóa ghế (Lock seat) cho một người dùng. Sử dụng PESSIMISTIC_WRITE để tránh lỗi Race Condition (nhiều người cùng mua 1 ghế).
     * Đây là cơ chế cốt lõi xử lý đồng thời (Concurrency) của hệ thống.
     */
    @Transactional
    public TicketResponse lockSeat(Long userId, Long seatId) {
        // 1. Acquire row-level lock (PESSIMISTIC_WRITE)
        Seat seat = seatRepository.findByIdWithLock(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + seatId));

        // 2. Validate event is ON_SALE
        Event event = seat.getZone().getEvent();
        if (event.getStatus() != EventStatus.ON_SALE) {
            throw new EventNotOnSaleException();
        }

        // 3. Check seat availability — this runs INSIDE the lock
        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            throw new SeatAlreadyTakenException();
        }

        // 4. Lock the seat
        seat.setStatus(SeatStatus.LOCKED);
        seat.setLockedAt(LocalDateTime.now());
        seat.setLockedByUserId(userId);
        seatRepository.save(seat);

        // 5. Create pending ticket
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Ticket ticket = Ticket.builder()
                .user(user)
                .seat(seat)
                .event(event)
                .price(seat.getZone().getPrice())
                .status(TicketStatus.PENDING_PAYMENT)
                .expiredAt(LocalDateTime.now().plusMinutes(10))
                .build();

        ticket = ticketRepository.save(ticket);

        // 6. Broadcast seat status change via WebSocket
        broadcastSeatUpdate(event.getId(), seat);

        log.info("Seat {} locked by user {} for event {}", seat.getLabel(), userId, event.getId());

        return toTicketResponse(ticket);
    }

    /**
     * Xác nhận thanh toán cho vé. Sinh mã QR nếu thanh toán thành công và chuyển trạng thái vé/ghế sang SOLD.
     */
    @Transactional
    public TicketResponse confirmPayment(Long userId, Long ticketId) {
        Ticket ticket = ticketRepository.findByIdAndUserId(ticketId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        if (ticket.getStatus() != TicketStatus.PENDING_PAYMENT) {
            throw new InvalidOperationException("Ticket is not pending payment");
        }

        if (ticket.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new InvalidOperationException("Payment time has expired. The seat has been released.");
        }

        // Mark ticket as PAID
        ticket.setStatus(TicketStatus.PAID);
        ticket.setPaidAt(LocalDateTime.now());

        // Generate QR Code
        ticket.setQrCode(qrCodeService.generate(ticket));

        // Update seat to SOLD
        Seat seat = ticket.getSeat();
        seat.setStatus(SeatStatus.SOLD);
        seat.setLockedAt(null);
        seat.setLockedByUserId(null);
        seatRepository.save(seat);

        ticket = ticketRepository.save(ticket);

        // Broadcast
        broadcastSeatUpdate(ticket.getEvent().getId(), seat);

        log.info("Ticket {} paid by user {} for seat {}", ticketId, userId, seat.getLabel());

        return toTicketResponse(ticket);
    }

    /**
     * Hủy vé đang chờ thanh toán và giải phóng ghế, cho phép người khác mua.
     */
    @Transactional
    public void cancelTicket(Long userId, Long ticketId) {
        Ticket ticket = ticketRepository.findByIdAndUserId(ticketId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        if (ticket.getStatus() != TicketStatus.PENDING_PAYMENT) {
            throw new InvalidOperationException("Only pending tickets can be cancelled");
        }

        ticket.setStatus(TicketStatus.CANCELLED);
        ticketRepository.save(ticket);

        // Release seat
        Seat seat = ticket.getSeat();
        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setLockedAt(null);
        seat.setLockedByUserId(null);
        seatRepository.save(seat);

        // Broadcast
        broadcastSeatUpdate(ticket.getEvent().getId(), seat);

        log.info("Ticket {} cancelled by user {}", ticketId, userId);
    }

    /**
     * Lấy danh sách các vé của một người dùng (sắp xếp mới nhất lên đầu).
     */
    public List<TicketResponse> getUserTickets(Long userId) {
        return ticketRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toTicketResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lấy thông tin chi tiết một vé cụ thể của người dùng.
     */
    public TicketResponse getTicketById(Long userId, Long ticketId) {
        Ticket ticket = ticketRepository.findByIdAndUserId(ticketId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        return toTicketResponse(ticket);
    }



    // ========== WebSocket Broadcasting ==========

    /**
     * Gửi thông báo thay đổi trạng thái ghế qua WebSocket để tất cả Frontend cập nhật theo thời gian thực.
     */
    private void broadcastSeatUpdate(Long eventId, Seat seat) {
        SeatStatusUpdate update = SeatStatusUpdate.builder()
                .seatId(seat.getId())
                .label(seat.getLabel())
                .zoneId(seat.getZone().getId())
                .status(seat.getStatus())
                .timestamp(LocalDateTime.now().toString())
                .build();

        messagingTemplate.convertAndSend("/topic/event/" + eventId + "/seats", update);
    }

    private TicketResponse toTicketResponse(Ticket ticket) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .userId(ticket.getUser().getId())
                .username(ticket.getUser().getUsername())
                .eventId(ticket.getEvent().getId())
                .eventName(ticket.getEvent().getName())
                .venue(ticket.getEvent().getVenue())
                .eventDate(ticket.getEvent().getEventDate())
                .seatId(ticket.getSeat().getId())
                .seatLabel(ticket.getSeat().getLabel())
                .zoneName(ticket.getSeat().getZone().getName())
                .zoneColor(ticket.getSeat().getZone().getColor())
                .status(ticket.getStatus())
                .price(ticket.getPrice())
                .qrCode(ticket.getQrCode())
                .createdAt(ticket.getCreatedAt())
                .paidAt(ticket.getPaidAt())
                .expiredAt(ticket.getExpiredAt())
                .build();
    }
}

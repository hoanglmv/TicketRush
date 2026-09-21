package app.service;

import app.dto.DynamicQRResponse;
import app.dto.SeatStatusUpdate;
import app.dto.TicketResponse;
import app.entity.Event;
import app.entity.Seat;
import app.entity.Ticket;
import app.entity.User;
import app.enums.EventStatus;
import app.enums.SeatStatus;
import app.enums.TicketStatus;
import app.exception.*;
import app.event.BookingEvent;
import app.event.PaymentEvent;
import app.repository.SeatRepository;
import app.repository.TicketRepository;
import app.repository.UserRepository;
import app.service.kafka.EventProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
    private final EventProducerService eventProducerService;

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

        // 7. Stream event to Kafka
        eventProducerService.sendBookingEvent(BookingEvent.builder()
                .eventType("LOCK_SEAT")
                .userId(userId)
                .seatId(seatId)
                .eventId(event.getId())
                .correlationId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .build());

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

        // Stream PaymentEvent to Kafka (triggers async notification / email worker)
        eventProducerService.sendPaymentEvent(PaymentEvent.builder()
                .eventType("PAYMENT_SUCCESS")
                .ticketId(ticket.getId())
                .userId(userId)
                .eventId(ticket.getEvent().getId())
                .seatLabel(seat.getLabel())
                .amount(ticket.getPrice())
                .userEmail(ticket.getUser() != null ? ticket.getUser().getEmail() : null)
                .correlationId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .build());

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

    /**
     * Sinh mã QR động (TOTP) thời gian thực có hiệu lực 30s chống chụp màn hình.
     */
    public DynamicQRResponse getDynamicQR(Long userId, Long ticketId) {
        Ticket ticket = ticketRepository.findByIdAndUserId(ticketId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        if (ticket.getStatus() != TicketStatus.PAID && ticket.getStatus() != TicketStatus.CHECKED_IN) {
            throw new IllegalStateException("Chỉ vé đã thanh toán thành công mới có mã QR vào cổng.");
        }

        return qrCodeService.generateDynamicQR(ticket);
    }

    /**
     * Quét và xác thực mã QR vé tại cổng sự kiện (Check-in).
     */
    @Transactional
    public TicketResponse checkinTicket(String qrPayload, String staffUsername) {
        Long ticketId = qrCodeService.verifyDynamicQR(qrPayload);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin vé ID: " + ticketId));

        if (ticket.getStatus() == TicketStatus.CHECKED_IN) {
            throw new IllegalStateException("Vé này đã được check-in vào cổng trước đó!");
        }

        if (ticket.getStatus() != TicketStatus.PAID) {
            throw new IllegalStateException("Vé chưa hoàn tất thanh toán (Trạng thái: " + ticket.getStatus() + ")");
        }

        ticket.setStatus(TicketStatus.CHECKED_IN);
        ticketRepository.save(ticket);
        log.info("Ticket #{} checked-in successfully by staff '{}'", ticketId, staffUsername);

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

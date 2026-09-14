package com.ticketrush.controller;

import com.ticketrush.dto.*;
import com.ticketrush.entity.User;
import com.ticketrush.service.AuthService;
import com.ticketrush.service.SeatBookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BookingController {

    private final SeatBookingService bookingService;
    private final AuthService authService;

    /**
     * API khóa ghế tạm thời (Lock Seat).
     * Khi người dùng chọn ghế, ghế sẽ bị khóa trong 1 khoảng thời gian (VD: 10 phút) để chờ thanh toán.
     */
    @PostMapping("/seats/{seatId}/lock")
    public ResponseEntity<ApiResponse<TicketResponse>> lockSeat(
            @PathVariable Long seatId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUser(userDetails.getUsername());
        TicketResponse ticket = bookingService.lockSeat(user.getId(), seatId);
        return ResponseEntity.ok(ApiResponse.success("Seat locked successfully. You have 10 minutes to pay.", ticket));
    }

    /**
     * API xác nhận thanh toán (Confirm Payment).
     * Sau khi người dùng thanh toán thành công, gọi API này để chuyển trạng thái vé sang BOOKED chính thức.
     */
    @PostMapping("/tickets/{ticketId}/confirm")
    public ResponseEntity<ApiResponse<TicketResponse>> confirmPayment(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUser(userDetails.getUsername());
        TicketResponse ticket = bookingService.confirmPayment(user.getId(), ticketId);
        return ResponseEntity.ok(ApiResponse.success("Payment confirmed! Your ticket is ready.", ticket));
    }

    /**
     * API hủy vé (Cancel Ticket).
     * Người dùng tự hủy vé chưa thanh toán, ghế sẽ được giải phóng cho người khác mua.
     */
    @DeleteMapping("/tickets/{ticketId}")
    public ResponseEntity<ApiResponse<Void>> cancelTicket(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUser(userDetails.getUsername());
        bookingService.cancelTicket(user.getId(), ticketId);
        return ResponseEntity.ok(ApiResponse.success("Ticket cancelled. Seat released.", null));
    }

    /**
     * API lấy danh sách vé của tôi (My Tickets).
     * Liệt kê tất cả các vé mà người dùng hiện tại đã đặt (gồm cả vé đang khóa chờ thanh toán và vé đã mua).
     */
    @GetMapping("/tickets/my")
    public ResponseEntity<ApiResponse<List<TicketResponse>>> getMyTickets(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(bookingService.getUserTickets(user.getId())));
    }

    /**
     * API lấy thông tin chi tiết một vé cụ thể.
     */
    @GetMapping("/tickets/{ticketId}")
    public ResponseEntity<ApiResponse<TicketResponse>> getTicket(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(bookingService.getTicketById(user.getId(), ticketId)));
    }

}

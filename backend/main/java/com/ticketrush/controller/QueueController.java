package com.ticketrush.controller;

import com.ticketrush.dto.ApiResponse;
import com.ticketrush.dto.QueueStatusResponse;
import com.ticketrush.entity.User;
import com.ticketrush.service.AuthService;
import com.ticketrush.service.VirtualQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final VirtualQueueService queueService;
    private final AuthService authService;

    /**
     * API tham gia vào hàng đợi ảo (Virtual Queue) của một sự kiện.
     * Dùng khi sự kiện quá tải, người dùng phải xếp hàng trước khi được mua vé.
     */
    @PostMapping("/{eventId}/join")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> joinQueue(
            @PathVariable Long eventId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUser(userDetails.getUsername());
        QueueStatusResponse status = queueService.joinQueue(user.getId(), eventId);
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    /**
     * API kiểm tra trạng thái xếp hàng của người dùng.
     * Trả về số thứ tự, số người đang chờ phía trước, và thời gian chờ dự kiến.
     */
    @GetMapping("/{eventId}/status")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> checkStatus(
            @PathVariable Long eventId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUser(userDetails.getUsername());
        QueueStatusResponse status = queueService.checkStatus(user.getId(), eventId);
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    /**
     * API rời khỏi hàng đợi.
     * Người dùng chủ động bỏ cuộc, nhường chỗ cho người khác.
     */
    @DeleteMapping("/{eventId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveQueue(
            @PathVariable Long eventId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUser(userDetails.getUsername());
        queueService.leaveQueue(user.getId(), eventId);
        return ResponseEntity.ok(ApiResponse.success("Left the queue", null));
    }
}

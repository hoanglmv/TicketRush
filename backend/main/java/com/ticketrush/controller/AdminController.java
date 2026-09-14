package com.ticketrush.controller;

import com.ticketrush.dto.*;
import com.ticketrush.enums.EventStatus;
import com.ticketrush.service.AdminDashboardService;
import com.ticketrush.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final EventService eventService;
    private final AdminDashboardService dashboardService;

    // ========== DASHBOARD ==========

    /**
     * API lấy thông tin thống kê tổng quan cho màn hình Admin Dashboard.
     * Trả về các số liệu như tổng số sự kiện, doanh thu, vé bán ra, v.v.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getDashboardStats()));
    }

    // ========== EVENT MANAGEMENT ==========

    /**
     * API lấy danh sách toàn bộ sự kiện hiện có trong hệ thống (dành cho Admin).
     */
    @GetMapping("/events")
    public ResponseEntity<ApiResponse<List<EventResponse>>> getAllEvents() {
        return ResponseEntity.ok(ApiResponse.success(eventService.getAllEvents()));
    }

    /**
     * API tạo mới một sự kiện.
     * Nhận vào thông tin cơ bản của sự kiện (tên, mô tả, thời gian, địa điểm, banner...).
     */
    @PostMapping("/events")
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(@Valid @RequestBody EventCreateRequest request) {
        EventResponse event = eventService.createEvent(request);
        return ResponseEntity.ok(ApiResponse.success("Event created", event));
    }

    /**
     * API cập nhật thông tin của một sự kiện đã tồn tại dựa trên ID.
     */
    @PutMapping("/events/{id}")
    public ResponseEntity<ApiResponse<EventResponse>> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody EventCreateRequest request) {
        EventResponse event = eventService.updateEvent(id, request);
        return ResponseEntity.ok(ApiResponse.success("Event updated", event));
    }

    /**
     * API thay đổi trạng thái của sự kiện (ví dụ: DRAFT, PUBLISHED, CANCELLED).
     */
    @PutMapping("/events/{id}/status")
    public ResponseEntity<ApiResponse<EventResponse>> updateStatus(
            @PathVariable Long id,
            @RequestParam EventStatus status) {
        EventResponse event = eventService.updateEventStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Status updated to " + status, event));
    }

    /**
     * API xóa một sự kiện khỏi hệ thống dựa trên ID.
     */
    @DeleteMapping("/events/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id);
        return ResponseEntity.ok(ApiResponse.success("Event deleted", null));
    }

    // ========== ZONE MANAGEMENT ==========

    /**
     * API tạo khu vực mới (Zone) cho một sự kiện cụ thể.
     * Đồng thời khởi tạo luôn danh sách ghế ngồi (Seats) cho khu vực đó dựa trên số hàng/cột.
     */
    @PostMapping("/events/{eventId}/zones")
    public ResponseEntity<ApiResponse<ZoneResponse>> createZone(
            @PathVariable Long eventId,
            @Valid @RequestBody ZoneCreateRequest request) {
        ZoneResponse zone = eventService.createZone(eventId, request);
        return ResponseEntity.ok(ApiResponse.success("Zone created with seats", zone));
    }

    /**
     * API sắp xếp lại thứ tự hiển thị của các khu vực trong một sự kiện.
     */
    @PutMapping("/events/{eventId}/zones/reorder")
    public ResponseEntity<ApiResponse<Void>> reorderZones(
            @PathVariable Long eventId,
            @RequestBody List<Long> zoneIds) {
        eventService.updateZoneOrder(eventId, zoneIds);
        return ResponseEntity.ok(ApiResponse.success("Zones reordered", null));
    }

    // ========== STATS ==========

    /**
     * API lấy thống kê chi tiết cho một sự kiện cụ thể (doanh thu, số vé bán được theo khu vực).
     */
    @GetMapping("/events/{eventId}/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEventStats(@PathVariable Long eventId) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getEventStats(eventId)));
    }

    /**
     * API lấy dữ liệu nhân khẩu học (demographics) của người mua vé trong một sự kiện (độ tuổi, giới tính).
     */
    @GetMapping("/events/{eventId}/demographics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDemographics(@PathVariable Long eventId) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getEventDemographics(eventId)));
    }
}

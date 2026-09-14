package com.ticketrush.controller;

import com.ticketrush.dto.*;
import com.ticketrush.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /**
     * API lấy danh sách các sự kiện công khai (Published).
     * Dành cho người dùng (khách truy cập) xem trên trang chủ.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<EventResponse>>> getPublicEvents() {
        return ResponseEntity.ok(ApiResponse.success(eventService.getPublicEvents()));
    }

    /**
     * API lấy thông tin chi tiết của một sự kiện dựa trên ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventResponse>> getEvent(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(eventService.getEvent(id)));
    }

    /**
     * API tìm kiếm và lọc sự kiện.
     * Hỗ trợ tìm kiếm theo từ khóa (q), danh mục (category), thành phố (city) và khoảng thời gian.
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<EventResponse>>> searchEvents(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime endDate) {
        return ResponseEntity.ok(ApiResponse.success(eventService.searchEvents(q, category, city, startDate, endDate)));
    }

    /**
     * API lấy danh sách các khu vực (Zones) của một sự kiện.
     */
    @GetMapping("/{eventId}/zones")
    public ResponseEntity<ApiResponse<List<ZoneResponse>>> getZones(@PathVariable Long eventId) {
        return ResponseEntity.ok(ApiResponse.success(eventService.getZonesByEvent(eventId)));
    }

    /**
     * API lấy danh sách toàn bộ ghế (Seats) của một sự kiện.
     */
    @GetMapping("/{eventId}/seats")
    public ResponseEntity<ApiResponse<List<SeatResponse>>> getSeats(@PathVariable Long eventId) {
        return ResponseEntity.ok(ApiResponse.success(eventService.getSeatsByEvent(eventId)));
    }
}

package com.ticketrush.dto;

import com.ticketrush.enums.SeatStatus;
import lombok.Builder;
import lombok.Data;

/**
 * WebSocket message payload for real-time seat status updates.
 */
@Data
@Builder
/** 
 * DTO (Data Transfer Object) - Lớp truyền tải dữ liệu.
 * Dùng để định nghĩa cấu trúc dữ liệu nhận từ Request hoặc trả về Response.
 */
public class SeatStatusUpdate {
    private Long seatId;
    private String label;
    private Long zoneId;
    private SeatStatus status;
    private String timestamp;
}

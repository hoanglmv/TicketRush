package com.ticketrush.dto;

import com.ticketrush.enums.TicketStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
/** 
 * DTO (Data Transfer Object) - Lớp truyền tải dữ liệu.
 * Dùng để định nghĩa cấu trúc dữ liệu nhận từ Request hoặc trả về Response.
 */
public class TicketResponse {
    private Long id;
    private Long userId;
    private String username;
    private Long eventId;
    private String eventName;
    private String venue;
    private LocalDateTime eventDate;
    private Long seatId;
    private String seatLabel;
    private String zoneName;
    private String zoneColor;
    private TicketStatus status;
    private BigDecimal price;
    private String qrCode;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime expiredAt;
    private boolean isResale;
    private BigDecimal resalePrice;
}

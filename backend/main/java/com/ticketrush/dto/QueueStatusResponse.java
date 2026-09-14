package com.ticketrush.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
/** 
 * DTO (Data Transfer Object) - Lớp truyền tải dữ liệu.
 * Dùng để định nghĩa cấu trúc dữ liệu nhận từ Request hoặc trả về Response.
 */
public class QueueStatusResponse {
    private Long eventId;
    private Long userId;
    private Long position;       // null if user has access
    private Long totalInQueue;
    private String accessToken;  // non-null when user granted access
    private boolean hasAccess;
    private String message;
}

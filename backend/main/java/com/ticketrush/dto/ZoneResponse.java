package com.ticketrush.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
/** 
 * DTO (Data Transfer Object) - Lớp truyền tải dữ liệu.
 * Dùng để định nghĩa cấu trúc dữ liệu nhận từ Request hoặc trả về Response.
 */
public class ZoneResponse {
    private Long id;
    private Long eventId;
    private String name;
    private String color;
    private BigDecimal price;
    private int totalRows;
    private int seatsPerRow;
    private int sortOrder;
    private long availableSeats;
    private long totalSeats;
}

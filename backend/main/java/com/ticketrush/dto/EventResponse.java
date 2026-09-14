package com.ticketrush.dto;

import com.ticketrush.enums.EventStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
/** 
 * DTO (Data Transfer Object) - Lớp truyền tải dữ liệu.
 * Dùng để định nghĩa cấu trúc dữ liệu nhận từ Request hoặc trả về Response.
 */
public class EventResponse {
    private Long id;
    private String name;
    private String description;
    private String venue;
    private String address;
    private String bannerUrl;
    private String category;
    private String city;
    private LocalDateTime eventDate;
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
    private EventStatus status;
    private boolean queueEnabled;
    private int queueBatchSize;
    private LocalDateTime createdAt;
    private List<ZoneResponse> zones;
    private long totalSeats;
    private long availableSeats;
    private long soldSeats;
    
    private boolean isHot;
    private List<String> images;
}

package com.ticketrush.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
/** 
 * DTO (Data Transfer Object) - Lớp truyền tải dữ liệu.
 * Dùng để định nghĩa cấu trúc dữ liệu nhận từ Request hoặc trả về Response.
 */
public class EventCreateRequest {
    @NotBlank(message = "Event name is required")
    private String name;

    private String description;
    private String venue;
    private String address;
    private String bannerUrl;
    private String category;
    private String city;

    @NotNull(message = "Event date is required")
    private LocalDateTime eventDate;

    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
    private boolean queueEnabled;
    private Integer queueBatchSize;

    private boolean isHot;
    private List<String> images;
}

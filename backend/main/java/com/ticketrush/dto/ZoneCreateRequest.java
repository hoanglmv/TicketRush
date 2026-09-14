package com.ticketrush.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
/** 
 * DTO (Data Transfer Object) - Lớp truyền tải dữ liệu.
 * Dùng để định nghĩa cấu trúc dữ liệu nhận từ Request hoặc trả về Response.
 */
public class ZoneCreateRequest {
    @NotBlank(message = "Zone name is required")
    private String name;

    @NotBlank(message = "Zone color is required")
    private String color;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", message = "Price must be non-negative")
    private BigDecimal price;

    @Min(value = 1, message = "Must have at least 1 row")
    private int totalRows;

    @Min(value = 1, message = "Must have at least 1 seat per row")
    private int seatsPerRow;

    private int sortOrder;
}

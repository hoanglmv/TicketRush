package com.ticketrush.dto;

import com.ticketrush.enums.Gender;
import lombok.Data;
import java.time.LocalDate;

@Data
/** 
 * DTO (Data Transfer Object) - Lớp truyền tải dữ liệu.
 * Dùng để định nghĩa cấu trúc dữ liệu nhận từ Request hoặc trả về Response.
 */
public class UserProfileUpdateRequest {
    private String fullName;
    private String phone;
    private LocalDate dateOfBirth;
    private Gender gender;
}

package com.ticketrush.dto;

import com.ticketrush.enums.Gender;
import com.ticketrush.enums.Role;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
/** 
 * DTO (Data Transfer Object) - Lớp truyền tải dữ liệu.
 * Dùng để định nghĩa cấu trúc dữ liệu nhận từ Request hoặc trả về Response.
 */
public class AuthResponse {
    private String accessToken;
    private String tokenType;
    private Long userId;
    private String username;
    private String email;
    private String fullName;
    private Role role;
    private Gender gender;
    private LocalDate dateOfBirth;
}

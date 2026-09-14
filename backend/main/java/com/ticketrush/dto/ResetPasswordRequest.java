package com.ticketrush.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
/** 
 * DTO (Data Transfer Object) - Lớp truyền tải dữ liệu.
 * Dùng để định nghĩa cấu trúc dữ liệu nhận từ Request hoặc trả về Response.
 */
public class ResetPasswordRequest {
    @NotBlank(message = "Email is required")
    @Email
    private String email;

    @NotBlank(message = "OTP is required")
    private String otp;

    @NotBlank(message = "New password is required")
    private String newPassword;
}

package com.ticketrush.controller;

import com.ticketrush.dto.ApiResponse;
import com.ticketrush.dto.UserProfileResponse;
import com.ticketrush.dto.UserProfileUpdateRequest;
import com.ticketrush.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * API lấy thông tin hồ sơ (Profile) của người dùng hiện tại đang đăng nhập.
     */
    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMyProfile(Authentication authentication) {
        UserProfileResponse profile = userService.getUserProfile(authentication.getName());
        return ApiResponse.success("Profile fetched successfully", profile);
    }

    /**
     * API cập nhật thông tin hồ sơ (Profile) của người dùng hiện tại (như ngày sinh, giới tính).
     */
    @PutMapping("/me")
    public ApiResponse<UserProfileResponse> updateMyProfile(Authentication authentication,
                                                            @RequestBody UserProfileUpdateRequest request) {
        UserProfileResponse profile = userService.updateUserProfile(authentication.getName(), request);
        return ApiResponse.success("Profile updated successfully", profile);
    }
}

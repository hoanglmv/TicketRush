package com.ticketrush.controller;

import com.ticketrush.dto.ApiResponse;
import com.ticketrush.service.SiteSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SiteSettingController {

    private final SiteSettingService siteSettingService;

    /**
     * API lấy danh sách các cấu hình chung của hệ thống (ví dụ: Logo, Banner trang chủ, Contact info).
     * Là API công khai, không cần đăng nhập.
     */
    @GetMapping("/public/settings")
    public ResponseEntity<ApiResponse<Map<String, String>>> getAllSettings() {
        return ResponseEntity.ok(ApiResponse.success("Settings fetched", siteSettingService.getAllSettingsAsMap()));
    }

    /**
     * API cập nhật các cấu hình chung của hệ thống.
     * Chỉ dành cho Admin, cho phép thay đổi giao diện/cấu hình động mà không cần deploy lại code.
     */
    // Since this is /admin/, we rely on Spring Security configuration protecting /api/admin/**
    @PostMapping("/admin/settings")
    public ResponseEntity<ApiResponse<Void>> updateSettings(@RequestBody Map<String, String> newSettings) {
        siteSettingService.updateSettings(newSettings);
        return ResponseEntity.ok(ApiResponse.success("Settings updated successfully", null));
    }
}

package com.ticketrush.service;

import com.ticketrush.entity.SiteSetting;
import com.ticketrush.repository.SiteSettingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SiteSettingService {

    private final SiteSettingRepository siteSettingRepository;

    /**
     * Hàm tự động chạy một lần khi khởi động ứng dụng để tạo các cấu hình mặc định (ảnh nền trang chủ) nếu chưa có trong DB.
     */
    @PostConstruct
    public void initDefaultSettings() {
        Map<String, String> defaults = new HashMap<>();
        // Hero Backgrounds
        defaults.put("hero_rock", "https://picsum.photos/seed/rockbg/1600/800");
        defaults.put("hero_hiphop", "https://picsum.photos/seed/hiphopbg/1600/800");
        defaults.put("hero_country", "https://picsum.photos/seed/countrybg/1600/800");
        defaults.put("hero_sports", "https://picsum.photos/seed/sportsbg/1600/800");
        defaults.put("hero_arts", "https://picsum.photos/seed/artsbg/1600/800");
        defaults.put("hero_family", "https://picsum.photos/seed/familybg/1600/800");
        defaults.put("hero_cities", "https://picsum.photos/seed/citybg/1600/800");
        defaults.put("hero_fallback", "https://picsum.photos/seed/fallback/1600/800");

        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            SiteSetting setting = siteSettingRepository.findBySettingKey(entry.getKey())
                .orElse(SiteSetting.builder().settingKey(entry.getKey()).build());
            // Force update to use picsum instead of unsplash
            if (setting.getSettingValue() == null || setting.getSettingValue().contains("unsplash")) {
                setting.setSettingValue(entry.getValue());
                siteSettingRepository.save(setting);
            }
        }
    }

    /**
     * Lấy toàn bộ cấu hình hệ thống dưới dạng Map (Key-Value) để dễ dàng trả về JSON.
     */
    public Map<String, String> getAllSettingsAsMap() {
        return siteSettingRepository.findAll().stream()
                .collect(Collectors.toMap(SiteSetting::getSettingKey, SiteSetting::getSettingValue));
    }

    /**
     * Cập nhật danh sách cấu hình. Nếu cấu hình nào chưa có thì sẽ tự động thêm mới vào DB.
     */
    @Transactional
    public void updateSettings(Map<String, String> newSettings) {
        for (Map.Entry<String, String> entry : newSettings.entrySet()) {
            SiteSetting setting = siteSettingRepository.findBySettingKey(entry.getKey())
                    .orElse(SiteSetting.builder().settingKey(entry.getKey()).build());
            setting.setSettingValue(entry.getValue());
            siteSettingRepository.save(setting);
        }
    }
}

package com.ticketrush.config;

import com.cloudinary.Cloudinary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class CloudinaryConfig {
    /**
     * Khởi tạo bean Cloudinary với các thông tin xác thực tĩnh.
     * Cung cấp đối tượng Cloudinary để upload và quản lý media trên nền tảng Cloudinary.
     */
    @Bean
    public Cloudinary cloudinary() {
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", "dsgogplr8");
        config.put("api_key", "691842235642811");
        config.put("api_secret", "3pzfTkZk4oS1i_bBOgTMq2ghLdM");
        return new Cloudinary(config);
    }
}

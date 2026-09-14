package com.ticketrush.repository;

import com.ticketrush.entity.SiteSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
/** 
 * Lớp Repository giao tiếp với Database.
 * Kế thừa Spring Data JPA để cung cấp các hàm thao tác dữ liệu (CRUD) tự động.
 */
public interface SiteSettingRepository extends JpaRepository<SiteSetting, Long> {
    Optional<SiteSetting> findBySettingKey(String settingKey);
}

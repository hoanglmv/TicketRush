package com.ticketrush.repository;

import com.ticketrush.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
/** 
 * Lớp Repository giao tiếp với Database.
 * Kế thừa Spring Data JPA để cung cấp các hàm thao tác dữ liệu (CRUD) tự động.
 */
public interface ZoneRepository extends JpaRepository<Zone, Long> {
    List<Zone> findByEventIdOrderBySortOrder(Long eventId);
}

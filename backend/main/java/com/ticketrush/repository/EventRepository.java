package com.ticketrush.repository;

import com.ticketrush.entity.Event;
import com.ticketrush.enums.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
/** 
 * Lớp Repository giao tiếp với Database.
 * Kế thừa Spring Data JPA để cung cấp các hàm thao tác dữ liệu (CRUD) tự động.
 */
public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByStatus(EventStatus status);

    List<Event> findByStatusIn(List<EventStatus> statuses);

    @Query("SELECT e FROM Event e WHERE (e.status IN :statuses) AND " +
           "(:keyword IS NULL OR LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(e.venue) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "(:category IS NULL OR LOWER(e.category) = LOWER(:category)) AND " +
           "(:city IS NULL OR LOWER(e.city) = LOWER(:city)) AND " +
           "(CAST(:startDate AS timestamp) IS NULL OR e.eventDate >= :startDate) AND " +
           "(CAST(:endDate AS timestamp) IS NULL OR e.eventDate <= :endDate)")
    List<Event> searchWithFilters(@Param("keyword") String keyword, 
                                 @Param("category") String category, 
                                 @Param("city") String city, 
                                 @Param("statuses") List<EventStatus> statuses,
                                 @Param("startDate") java.time.LocalDateTime startDate,
                                 @Param("endDate") java.time.LocalDateTime endDate);

    @Query("SELECT e FROM Event e WHERE e.status = 'ON_SALE' AND e.queueEnabled = true")
    List<Event> findOnSaleWithQueueEnabled();
}

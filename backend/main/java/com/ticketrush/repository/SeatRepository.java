package com.ticketrush.repository;

import com.ticketrush.entity.Seat;
import com.ticketrush.enums.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
/** 
 * Lớp Repository giao tiếp với Database.
 * Kế thừa Spring Data JPA để cung cấp các hàm thao tác dữ liệu (CRUD) tự động.
 */
public interface SeatRepository extends JpaRepository<Seat, Long> {

    /**
     * PESSIMISTIC_WRITE lock — Row-Level Locking for concurrent seat booking.
     * This is the core mechanism to prevent race conditions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id = :id")
    Optional<Seat> findByIdWithLock(@Param("id") Long id);

    List<Seat> findByZoneId(Long zoneId);

    @Query("SELECT s FROM Seat s JOIN s.zone z WHERE z.event.id = :eventId ORDER BY z.sortOrder, s.rowNumber, s.colNumber")
    List<Seat> findByEventId(@Param("eventId") Long eventId);

    @Query("SELECT COUNT(s) FROM Seat s JOIN s.zone z WHERE z.event.id = :eventId AND s.status = :status")
    long countByEventIdAndStatus(@Param("eventId") Long eventId, @Param("status") SeatStatus status);

    List<Seat> findByStatusAndLockedAtBefore(SeatStatus status, LocalDateTime cutoff);

    @Query("SELECT s FROM Seat s JOIN s.zone z WHERE z.event.id = :eventId AND s.status = :status")
    List<Seat> findByEventIdAndStatus(@Param("eventId") Long eventId, @Param("status") SeatStatus status);
}

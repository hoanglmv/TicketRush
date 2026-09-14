package com.ticketrush.repository;

import com.ticketrush.entity.Seat;
import com.ticketrush.entity.Ticket;
import com.ticketrush.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByEventId(Long eventId);

    Optional<Ticket> findByIdAndUserId(Long id, Long userId);

    Optional<Ticket> findBySeatAndStatus(Seat seat, TicketStatus status);

    List<Ticket> findByStatusAndExpiredAtBefore(TicketStatus status, LocalDateTime cutoff);

    @Query("SELECT t FROM Ticket t WHERE t.event.id = :eventId AND t.status = 'PAID'")
    List<Ticket> findPaidTicketsByEventId(@Param("eventId") Long eventId);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.event.id = :eventId AND t.status = 'PAID'")
    long countPaidTicketsByEventId(@Param("eventId") Long eventId);

    @Query("SELECT SUM(t.price) FROM Ticket t WHERE t.event.id = :eventId AND t.status = 'PAID'")
    java.math.BigDecimal sumRevenueByEventId(@Param("eventId") Long eventId);

    @Query("SELECT SUM(t.price) FROM Ticket t WHERE t.status = 'PAID'")
    java.math.BigDecimal sumTotalRevenue();
}

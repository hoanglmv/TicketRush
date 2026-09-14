package com.ticketrush.entity;

import com.ticketrush.enums.SeatStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "seats", indexes = {
    @Index(name = "idx_seat_zone", columnList = "zone_id"),
    @Index(name = "idx_seat_status", columnList = "status"),
    @Index(name = "idx_seat_locked_at", columnList = "lockedAt")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
/** 
 * Lớp thực thể (Entity) đại diện cho bảng trong Database.
 * Sử dụng JPA để ánh xạ các thuộc tính thành các cột trong cơ sở dữ liệu.
 */
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @Column(name = "`row_number`", nullable = false)
    private int rowNumber;

    @Column(nullable = false)
    private int colNumber;

    @Column(nullable = false, length = 10)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private SeatStatus status;

    private LocalDateTime lockedAt;

    private Long lockedByUserId;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = SeatStatus.AVAILABLE;
        }
    }
}

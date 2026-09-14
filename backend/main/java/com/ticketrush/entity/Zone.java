package com.ticketrush.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "zones")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
/** 
 * Lớp thực thể (Entity) đại diện cho bảng trong Database.
 * Sử dụng JPA để ánh xạ các thuộc tính thành các cột trong cơ sở dữ liệu.
 */
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 10)
    private String color;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int totalRows;

    @Column(nullable = false)
    private int seatsPerRow;

    @Column(nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @OneToMany(mappedBy = "zone", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Seat> seats = new ArrayList<>();
}

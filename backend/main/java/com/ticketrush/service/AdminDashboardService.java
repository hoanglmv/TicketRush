package com.ticketrush.service;

import com.ticketrush.entity.Ticket;
import com.ticketrush.entity.User;
import com.ticketrush.enums.Gender;
import com.ticketrush.enums.SeatStatus;
import com.ticketrush.repository.EventRepository;
import com.ticketrush.repository.SeatRepository;
import com.ticketrush.repository.TicketRepository;
import com.ticketrush.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final TicketRepository ticketRepository;
    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    /**
     * Lấy các thống kê tổng quan cho toàn bộ hệ thống (số sự kiện, tổng doanh thu, số người dùng).
     */
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        long totalEvents = eventRepository.count();
        BigDecimal totalRevenue = ticketRepository.sumTotalRevenue();

        stats.put("totalEvents", totalEvents);
        stats.put("totalRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO);
        stats.put("totalUsers", userRepository.count());

        return stats;
    }

    /**
     * Lấy thống kê chi tiết cho một sự kiện (số ghế trống, số ghế đã bán, tỷ lệ lấp đầy, doanh thu).
     */
    public Map<String, Object> getEventStats(Long eventId) {
        Map<String, Object> stats = new HashMap<>();

        long totalSeats = seatRepository.countByEventIdAndStatus(eventId, SeatStatus.AVAILABLE)
                + seatRepository.countByEventIdAndStatus(eventId, SeatStatus.LOCKED)
                + seatRepository.countByEventIdAndStatus(eventId, SeatStatus.SOLD);
        long availableSeats = seatRepository.countByEventIdAndStatus(eventId, SeatStatus.AVAILABLE);
        long lockedSeats = seatRepository.countByEventIdAndStatus(eventId, SeatStatus.LOCKED);
        long soldSeats = seatRepository.countByEventIdAndStatus(eventId, SeatStatus.SOLD);

        BigDecimal revenue = ticketRepository.sumRevenueByEventId(eventId);

        stats.put("totalSeats", totalSeats);
        stats.put("availableSeats", availableSeats);
        stats.put("lockedSeats", lockedSeats);
        stats.put("soldSeats", soldSeats);
        stats.put("occupancyRate", totalSeats > 0 ? (double) soldSeats / totalSeats * 100 : 0);
        stats.put("revenue", revenue != null ? revenue : BigDecimal.ZERO);

        return stats;
    }

    /**
     * Phân tích dữ liệu nhân khẩu học (độ tuổi, giới tính) của những người đã mua vé cho sự kiện.
     */
    public Map<String, Object> getEventDemographics(Long eventId) {
        List<Ticket> paidTickets = ticketRepository.findPaidTicketsByEventId(eventId);
        Map<String, Object> demographics = new HashMap<>();

        // Gender distribution
        Map<String, Long> genderDist = paidTickets.stream()
                .map(t -> t.getUser().getGender())
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Gender::name, Collectors.counting()));
        demographics.put("gender", genderDist);

        // Age distribution
        Map<String, Long> ageDist = new LinkedHashMap<>();
        ageDist.put("Under 18", 0L);
        ageDist.put("18-24", 0L);
        ageDist.put("25-34", 0L);
        ageDist.put("35-44", 0L);
        ageDist.put("45+", 0L);

        for (Ticket ticket : paidTickets) {
            User user = ticket.getUser();
            if (user.getDateOfBirth() != null) {
                int age = Period.between(user.getDateOfBirth(), LocalDate.now()).getYears();
                String group;
                if (age < 18) group = "Under 18";
                else if (age <= 24) group = "18-24";
                else if (age <= 34) group = "25-34";
                else if (age <= 44) group = "35-44";
                else group = "45+";
                ageDist.merge(group, 1L, Long::sum);
            }
        }
        demographics.put("ageGroups", ageDist);

        demographics.put("totalAttendees", paidTickets.size());

        return demographics;
    }
}

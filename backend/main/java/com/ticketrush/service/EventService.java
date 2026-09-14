package com.ticketrush.service;

import com.ticketrush.dto.*;
import com.ticketrush.entity.Event;
import com.ticketrush.entity.Seat;
import com.ticketrush.entity.Zone;
import com.ticketrush.enums.EventStatus;
import com.ticketrush.enums.SeatStatus;
import com.ticketrush.exception.InvalidOperationException;
import com.ticketrush.exception.ResourceNotFoundException;
import com.ticketrush.repository.EventRepository;
import com.ticketrush.repository.SeatRepository;
import com.ticketrush.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final ZoneRepository zoneRepository;
    private final SeatRepository seatRepository;
    private final com.ticketrush.repository.TicketRepository ticketRepository;

    // ========== EVENT CRUD ==========

    /**
     * Tạo mới một sự kiện (trạng thái mặc định là DRAFT).
     */
    @Transactional
    public EventResponse createEvent(EventCreateRequest request) {
        validateEventDates(request.getEventDate(), request.getSaleStartTime(), request.getSaleEndTime());

        Event event = Event.builder()
                .name(request.getName())
                .description(request.getDescription())
                .venue(request.getVenue())
                .address(request.getAddress())
                .bannerUrl(request.getBannerUrl())
                .category(request.getCategory())
                .city(request.getCity())
                .eventDate(request.getEventDate())
                .saleStartTime(request.getSaleStartTime())
                .saleEndTime(request.getSaleEndTime())
                .status(EventStatus.DRAFT)
                .queueEnabled(request.isQueueEnabled())
                .queueBatchSize(request.getQueueBatchSize() != null ? request.getQueueBatchSize() : 50)
                .isHot(request.isHot())
                .images(request.getImages() != null ? request.getImages() : new ArrayList<>())
                .build();

        event = eventRepository.save(event);
        return toEventResponse(event);
    }

    /**
     * Cập nhật thông tin chi tiết của sự kiện.
     */
    @Transactional
    public EventResponse updateEvent(Long eventId, EventCreateRequest request) {
        validateEventDates(request.getEventDate(), request.getSaleStartTime(), request.getSaleEndTime());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        event.setName(request.getName());
        event.setDescription(request.getDescription());
        event.setVenue(request.getVenue());
        event.setAddress(request.getAddress());
        event.setBannerUrl(request.getBannerUrl());
        event.setCategory(request.getCategory());
        event.setCity(request.getCity());
        event.setEventDate(request.getEventDate());
        event.setSaleStartTime(request.getSaleStartTime());
        event.setSaleEndTime(request.getSaleEndTime());
        event.setQueueEnabled(request.isQueueEnabled());
        if (request.getQueueBatchSize() != null) {
            event.setQueueBatchSize(request.getQueueBatchSize());
        }
        event.setHot(request.isHot());
        if (request.getImages() != null) {
            event.getImages().clear();
            event.getImages().addAll(request.getImages());
        }

        event = eventRepository.save(event);
        return toEventResponse(event);
    }

    /**
     * Đổi trạng thái sự kiện (ví dụ từ DRAFT sang PUBLISHED).
     */
    @Transactional
    public EventResponse updateEventStatus(Long eventId, EventStatus newStatus) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        validateStatusTransition(event.getStatus(), newStatus);
        event.setStatus(newStatus);
        event = eventRepository.save(event);
        return toEventResponse(event);
    }

    /**
     * Lấy thông tin chi tiết của một sự kiện bằng ID.
     */
    public EventResponse getEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        return toEventResponse(event);
    }

    /**
     * Xóa sự kiện và toàn bộ vé/dữ liệu liên quan.
     */
    @Transactional
    public void deleteEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        ticketRepository.deleteByEventId(eventId);
        eventRepository.delete(event);
    }

    /**
     * Lấy danh sách sự kiện đang công khai (để hiển thị lên trang chủ).
     */
    public List<EventResponse> getPublicEvents() {
        List<EventStatus> publicStatuses = List.of(EventStatus.PUBLISHED, EventStatus.ON_SALE);
        return eventRepository.findByStatusIn(publicStatuses).stream()
                .map(this::toEventResponse)
                .collect(Collectors.toList());
    }

    public List<EventResponse> getAllEvents() {
        return eventRepository.findAll().stream()
                .map(this::toEventResponse)
                .collect(Collectors.toList());
    }

    /**
     * Tìm kiếm sự kiện theo từ khóa, danh mục, địa điểm hoặc thời gian diễn ra.
     */
    public List<EventResponse> searchEvents(String keyword, String category, String city, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate) {
        List<EventStatus> publicStatuses = List.of(EventStatus.PUBLISHED, EventStatus.ON_SALE);
        return eventRepository.searchWithFilters(keyword, category, city, publicStatuses, startDate, endDate).stream()
                .map(this::toEventResponse)
                .collect(Collectors.toList());
    }

    // ========== ZONE MANAGEMENT ==========

    /**
     * Thêm một khu vực (Zone) mới vào sự kiện (ví dụ: khu VIP, khu thường).
     * Sẽ tự động gọi hàm sinh ra ghế (Seat) tương ứng cho khu vực này.
     */
    @Transactional
    public ZoneResponse createZone(Long eventId, ZoneCreateRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new InvalidOperationException("Zones can only be added to DRAFT events");
        }

        Zone zone = Zone.builder()
                .event(event)
                .name(request.getName())
                .color(request.getColor())
                .price(request.getPrice())
                .totalRows(request.getTotalRows())
                .seatsPerRow(request.getSeatsPerRow())
                .sortOrder(request.getSortOrder())
                .build();

        zone = zoneRepository.save(zone);

        // Auto-generate seats
        generateSeats(zone);

        return toZoneResponse(zone);
    }

    /**
     * Lấy danh sách các khu vực của sự kiện.
     */
    public List<ZoneResponse> getZonesByEvent(Long eventId) {
        return zoneRepository.findByEventIdOrderBySortOrder(eventId).stream()
                .map(this::toZoneResponse)
                .collect(Collectors.toList());
    }

    /**
     * Thay đổi thứ tự hiển thị của các khu vực.
     */
    @Transactional
    public void updateZoneOrder(Long eventId, List<Long> zoneIds) {
        for (int i = 0; i < zoneIds.size(); i++) {
            Long zoneId = zoneIds.get(i);
            Zone zone = zoneRepository.findById(zoneId)
                    .orElseThrow(() -> new ResourceNotFoundException("Zone not found"));
            if (zone.getEvent().getId().equals(eventId)) {
                zone.setSortOrder(i);
                zoneRepository.save(zone);
            }
        }
    }

    // ========== SEAT LISTING ==========

    /**
     * Lấy toàn bộ danh sách ghế của một sự kiện.
     */
    public List<SeatResponse> getSeatsByEvent(Long eventId) {
        return seatRepository.findByEventId(eventId).stream()
                .map(this::toSeatResponse)
                .collect(Collectors.toList());
    }

    // ========== PRIVATE HELPERS ==========

    private void generateSeats(Zone zone) {
        List<Seat> seats = new ArrayList<>();
        for (int row = 0; row < zone.getTotalRows(); row++) {
            char rowChar = (char) ('A' + row);
            for (int col = 1; col <= zone.getSeatsPerRow(); col++) {
                Seat seat = Seat.builder()
                        .zone(zone)
                        .rowNumber(row + 1)
                        .colNumber(col)
                        .label(String.valueOf(rowChar) + col)
                        .status(SeatStatus.AVAILABLE)
                        .build();
                seats.add(seat);
            }
        }
        seatRepository.saveAll(seats);
    }

    private void validateEventDates(java.time.LocalDateTime eventDate, java.time.LocalDateTime saleStart, java.time.LocalDateTime saleEnd) {
        if (eventDate != null && eventDate.isBefore(java.time.LocalDateTime.now())) {
            throw new InvalidOperationException("Thời gian sự kiện phải sau thời điểm hiện tại.");
        }
        if (saleStart != null && saleEnd != null) {
            if (saleStart.isAfter(saleEnd) || saleStart.isEqual(saleEnd)) {
                throw new InvalidOperationException("Thời gian mở bán phải diễn ra trước thời gian đóng bán vé.");
            }
            if (eventDate != null && (saleEnd.isAfter(eventDate) || saleEnd.isEqual(eventDate))) {
                throw new InvalidOperationException("Thời gian đóng bán vé phải trước khi sự kiện diễn ra.");
            }
        }
    }

    private void validateStatusTransition(EventStatus current, EventStatus next) {
        boolean valid = switch (current) {
            case DRAFT -> next == EventStatus.PUBLISHED;
            case PUBLISHED -> next == EventStatus.ON_SALE || next == EventStatus.CANCELLED;
            case ON_SALE -> next == EventStatus.COMPLETED || next == EventStatus.CANCELLED;
            default -> false;
        };
        if (!valid) {
            throw new InvalidOperationException(
                    String.format("Cannot transition from %s to %s", current, next));
        }
    }

    public EventResponse toEventResponse(Event event) {
        long totalSeats = seatRepository.countByEventIdAndStatus(event.getId(), SeatStatus.AVAILABLE)
                + seatRepository.countByEventIdAndStatus(event.getId(), SeatStatus.LOCKED)
                + seatRepository.countByEventIdAndStatus(event.getId(), SeatStatus.SOLD);
        long availableSeats = seatRepository.countByEventIdAndStatus(event.getId(), SeatStatus.AVAILABLE);
        long soldSeats = seatRepository.countByEventIdAndStatus(event.getId(), SeatStatus.SOLD);

        List<ZoneResponse> zones = zoneRepository.findByEventIdOrderBySortOrder(event.getId())
                .stream().map(this::toZoneResponse).collect(Collectors.toList());

        return EventResponse.builder()
                .id(event.getId())
                .name(event.getName())
                .description(event.getDescription())
                .venue(event.getVenue())
                .address(event.getAddress())
                .bannerUrl(event.getBannerUrl())
                .category(event.getCategory())
                .city(event.getCity())
                .eventDate(event.getEventDate())
                .saleStartTime(event.getSaleStartTime())
                .saleEndTime(event.getSaleEndTime())
                .status(event.getStatus())
                .queueEnabled(event.isQueueEnabled())
                .queueBatchSize(event.getQueueBatchSize())
                .createdAt(event.getCreatedAt())
                .zones(zones)
                .totalSeats(totalSeats)
                .availableSeats(availableSeats)
                .soldSeats(soldSeats)
                .isHot(event.isHot())
                .images(event.getImages())
                .build();
    }

    private ZoneResponse toZoneResponse(Zone zone) {
        long total = zone.getTotalRows() * zone.getSeatsPerRow();
        long available = seatRepository.findByZoneId(zone.getId()).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .count();

        return ZoneResponse.builder()
                .id(zone.getId())
                .eventId(zone.getEvent().getId())
                .name(zone.getName())
                .color(zone.getColor())
                .price(zone.getPrice())
                .totalRows(zone.getTotalRows())
                .seatsPerRow(zone.getSeatsPerRow())
                .sortOrder(zone.getSortOrder())
                .totalSeats(total)
                .availableSeats(available)
                .build();
    }

    private SeatResponse toSeatResponse(Seat seat) {
        return SeatResponse.builder()
                .id(seat.getId())
                .zoneId(seat.getZone().getId())
                .zoneName(seat.getZone().getName())
                .zoneColor(seat.getZone().getColor())
                .rowNumber(seat.getRowNumber())
                .colNumber(seat.getColNumber())
                .label(seat.getLabel())
                .status(seat.getStatus())
                .price(seat.getZone().getPrice())
                .build();
    }
}

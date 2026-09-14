package com.ticketrush.config;

import com.ticketrush.entity.User;
import com.ticketrush.enums.Role;
import com.ticketrush.enums.EventStatus;
import com.ticketrush.repository.UserRepository;
import com.ticketrush.repository.EventRepository;
import com.ticketrush.service.EventService;
import com.ticketrush.dto.EventCreateRequest;
import com.ticketrush.dto.ZoneCreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final PasswordEncoder passwordEncoder;

    /**
     * Hàm tự động chạy khi ứng dụng khởi động.
     * Khởi tạo tài khoản Admin mặc định và tạo sẵn một số sự kiện mẫu (seed data) nếu DB trống.
     */
    @Bean
    public CommandLineRunner initData(UserRepository userRepository, EventRepository eventRepository, EventService eventService) {
        return args -> {
            // Create admin user if not exists
            if (!userRepository.existsByUsername("admin")) {
                User admin = User.builder()
                        .username("admin")
                        .password(passwordEncoder.encode("admin123"))
                        .email("admin@ticketrush.com")
                        .fullName("System Administrator")
                        .role(Role.ROLE_ADMIN)
                        .isActive(true)
                        .build();
                userRepository.save(admin);
                log.info("Admin user created: admin / admin123");
            }

            // Force update all old Unsplash events
            eventRepository.findAll().forEach(e -> {
                if (e.getBannerUrl() != null && e.getBannerUrl().contains("unsplash")) {
                    e.setBannerUrl("https://picsum.photos/seed/event" + e.getId() + "/1600/800");
                    eventRepository.save(e);
                }
            });

            // Seed Sample Events
            if (eventRepository.count() == 0) {
                log.info("Seeding sample events...");
                
                // Event 1: Los Tigres del Norte (Match the frontend mock exactly for wow factor)
                EventCreateRequest ev1 = new EventCreateRequest();
                ev1.setName("Los Tigres del Norte");
                ev1.setCategory("WORLD");
                ev1.setVenue("Madison Square Garden");
                ev1.setCity("New York");
                ev1.setBannerUrl("https://picsum.photos/seed/event-tigres/1600/800");
                ev1.setEventDate(LocalDateTime.now().plusDays(5));
                ev1.setSaleStartTime(LocalDateTime.now().minusDays(1));
                ev1.setSaleEndTime(LocalDateTime.now().plusDays(4));
                var savedEv1 = eventService.createEvent(ev1);
                
                ZoneCreateRequest z1 = new ZoneCreateRequest();
                z1.setName("GA Pit"); z1.setColor("#e74c3c"); z1.setPrice(new BigDecimal("150.00")); z1.setTotalRows(5); z1.setSeatsPerRow(20); z1.setSortOrder(1);
                eventService.createZone(savedEv1.getId(), z1);
                
                eventService.updateEventStatus(savedEv1.getId(), EventStatus.PUBLISHED);
                eventService.updateEventStatus(savedEv1.getId(), EventStatus.ON_SALE);

                // Event 2: The Weeknd
                EventCreateRequest ev2 = new EventCreateRequest();
                ev2.setName("The Weeknd - After Hours Tour");
                ev2.setCategory("POP");
                ev2.setVenue("SoFi Stadium");
                ev2.setCity("Los Angeles");
                ev2.setBannerUrl("https://picsum.photos/seed/event-weeknd/1600/800");
                ev2.setEventDate(LocalDateTime.now().plusDays(10));
                ev2.setSaleStartTime(LocalDateTime.now().minusDays(1));
                ev2.setSaleEndTime(LocalDateTime.now().plusDays(9));
                var savedEv2 = eventService.createEvent(ev2);
                
                ZoneCreateRequest z2 = new ZoneCreateRequest();
                z2.setName("VIP"); z2.setColor("#f1c40f"); z2.setPrice(new BigDecimal("350.00")); z2.setTotalRows(3); z2.setSeatsPerRow(10); z2.setSortOrder(1);
                eventService.createZone(savedEv2.getId(), z2);
                ZoneCreateRequest z3 = new ZoneCreateRequest();
                z3.setName("Level 100"); z3.setColor("#3498db"); z3.setPrice(new BigDecimal("120.00")); z3.setTotalRows(10); z3.setSeatsPerRow(20); z3.setSortOrder(2);
                eventService.createZone(savedEv2.getId(), z3);
                
                eventService.updateEventStatus(savedEv2.getId(), EventStatus.PUBLISHED);
                eventService.updateEventStatus(savedEv2.getId(), EventStatus.ON_SALE);
                // Event 3: Taylor Swift (Future)
                EventCreateRequest ev3 = new EventCreateRequest();
                ev3.setName("Taylor Swift - The Eras Tour");
                ev3.setCategory("POP");
                ev3.setVenue("Wembley Stadium");
                ev3.setCity("London");
                ev3.setBannerUrl("https://picsum.photos/seed/event-taylor/1600/800");
                ev3.setEventDate(LocalDateTime.now().plusDays(20));
                ev3.setSaleStartTime(LocalDateTime.now().minusDays(2));
                ev3.setSaleEndTime(LocalDateTime.now().plusDays(19));
                var savedEv3 = eventService.createEvent(ev3);
                
                ZoneCreateRequest z4 = new ZoneCreateRequest();
                z4.setName("Front Row VIP"); z4.setColor("#e84393"); z4.setPrice(new BigDecimal("800.00")); z4.setTotalRows(2); z4.setSeatsPerRow(15); z4.setSortOrder(1);
                eventService.createZone(savedEv3.getId(), z4);
                
                eventService.updateEventStatus(savedEv3.getId(), EventStatus.PUBLISHED);
                eventService.updateEventStatus(savedEv3.getId(), EventStatus.ON_SALE);

                // Event 4: Coldplay (Future)
                EventCreateRequest ev4 = new EventCreateRequest();
                ev4.setName("Coldplay - Music Of The Spheres");
                ev4.setCategory("ROCK");
                ev4.setVenue("Stade de France");
                ev4.setCity("Paris");
                ev4.setBannerUrl("https://picsum.photos/seed/event-coldplay/1600/800");
                ev4.setEventDate(LocalDateTime.now().plusDays(30));
                ev4.setSaleStartTime(LocalDateTime.now().minusDays(5));
                ev4.setSaleEndTime(LocalDateTime.now().plusDays(29));
                var savedEv4 = eventService.createEvent(ev4);
                
                ZoneCreateRequest z5 = new ZoneCreateRequest();
                z5.setName("Standing"); z5.setColor("#00cec9"); z5.setPrice(new BigDecimal("90.00")); z5.setTotalRows(8); z5.setSeatsPerRow(25); z5.setSortOrder(1);
                eventService.createZone(savedEv4.getId(), z5);
                
                eventService.updateEventStatus(savedEv4.getId(), EventStatus.PUBLISHED);
                eventService.updateEventStatus(savedEv4.getId(), EventStatus.ON_SALE);

                // Event 5: BLACKPINK (Past Event)
                EventCreateRequest ev5 = new EventCreateRequest();
                ev5.setName("BLACKPINK - BORN PINK World Tour");
                ev5.setCategory("POP");
                ev5.setVenue("My Dinh National Stadium");
                ev5.setCity("Hanoi");
                ev5.setBannerUrl("https://picsum.photos/seed/event-bp/1600/800");
                ev5.setEventDate(LocalDateTime.now().minusDays(10)); // IN THE PAST
                ev5.setSaleStartTime(LocalDateTime.now().minusDays(40));
                ev5.setSaleEndTime(LocalDateTime.now().minusDays(11));
                var savedEv5 = eventService.createEvent(ev5);
                
                ZoneCreateRequest z6 = new ZoneCreateRequest();
                z6.setName("VIP Pink"); z6.setColor("#ff7675"); z6.setPrice(new BigDecimal("400.00")); z6.setTotalRows(5); z6.setSeatsPerRow(20); z6.setSortOrder(1);
                eventService.createZone(savedEv5.getId(), z6);
                
                eventService.updateEventStatus(savedEv5.getId(), EventStatus.PUBLISHED);
                eventService.updateEventStatus(savedEv5.getId(), EventStatus.ON_SALE);

                log.info("Finished seeding events.");
            }
        };
    }
}

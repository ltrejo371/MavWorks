package com.mavworks.service;

import com.mavworks.model.BookingStatus;
import com.mavworks.repository.BookingRepository;
import java.time.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
public class AppointmentLifecycle {
    private final BookingRepository bookings;
    private final BookingService service;
    public AppointmentLifecycle(BookingRepository bookings, BookingService service) { this.bookings = bookings; this.service = service; }
    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    public void advance() {
        Instant now = Instant.now();
        for (Long id : bookings.findDue(BookingStatus.REQUESTED, BookingStatus.CONFIRMED, now, now.minus(Duration.ofHours(48)))) {
            try { service.advanceLifecycle(id); }
            catch (RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(getClass()).error("Unable to advance appointment {}", id, ex);
            }
        }
    }
}

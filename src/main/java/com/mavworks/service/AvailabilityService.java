package com.mavworks.service;

import com.mavworks.model.*;
import com.mavworks.repository.*;
import com.mavworks.security.CurrentUser;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AvailabilityService {
    public static final int SLOT_MINUTES = 30;
    public static final int SLOTS_PER_DAY = 24 * 60 / SLOT_MINUTES;
    public static class SaveException extends RuntimeException {
        public SaveException(Throwable cause) { super("Unable to save availability changes. Please try again later.", cause); }
    }
    private final CurrentUser current;
    private final UserRepository users;
    private final AvailabilityRepository slots;
    public AvailabilityService(CurrentUser current, UserRepository users, AvailabilityRepository slots) {
        this.current = current; this.users = users; this.slots = slots;
    }
    private User user() {
        return current.username().flatMap(users::findByUsername)
                .orElseThrow(() -> new AccessDeniedException("Sign in to manage availability."));
    }
    public Map<LocalDate, Set<Integer>> month(YearMonth month) {
        return slots.findByUserIdAndDateBetween(user().getId(), month.atDay(1), month.atEndOfMonth()).stream()
                .collect(Collectors.groupingBy(Availability::getDate, Collectors.mapping(Availability::getSlot, Collectors.toSet())));
    }
    public Set<Integer> day(LocalDate date) {
        return slots.findByUserIdAndDate(user().getId(), date).stream().map(Availability::getSlot).collect(Collectors.toSet());
    }
    @Transactional
    public void setAvailable(LocalDate date, int slot, boolean available) {
        User viewer = user();
        users.findForMessaging(viewer.getUsername()).orElseThrow();
        validate(date, slot);
        boolean exists = day(date).contains(slot);
        if (available && !exists) slots.saveAndFlush(new Availability(viewer, date, slot));
        if (!available && exists) { slots.deleteByUserIdAndDateAndSlot(viewer.getId(), date, slot); slots.flush(); }
    }
    public static void validate(LocalDate date, int slot) {
        if (date == null || date.isBefore(LocalDate.now(BookingService.BOOKING_ZONE)) || slot < 0 || slot >= SLOTS_PER_DAY)
            throw new IllegalArgumentException("Choose a current or future date and a 30-minute slot.");
        BookingService.toInstant(date.atStartOfDay().plusMinutes(slot * (long) AvailabilityService.SLOT_MINUTES));
        BookingService.toInstant(date.atStartOfDay().plusMinutes((slot + 1) * (long) AvailabilityService.SLOT_MINUTES));
    }
}

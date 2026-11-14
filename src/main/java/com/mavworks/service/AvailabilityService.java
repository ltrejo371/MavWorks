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

    @Transactional
    public int setAvailableRange(LocalDate firstDate, LocalDate lastDate, Set<DayOfWeek> weekdays, int firstSlot, int endSlot, boolean available) {

        if (firstDate == null || lastDate == null || firstDate.isBefore(LocalDate.now(BookingService.BOOKING_ZONE)) || lastDate.isBefore(firstDate)) {
            throw new IllegalArgumentException("Choose a current or future date range.");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(firstDate, lastDate) >= 90) {
            throw new IllegalArgumentException("Choose a date range of at most 90 days.");
        }
        if (weekdays == null || weekdays.isEmpty() || weekdays.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Choose at least one weekday.");
        }
        if (firstSlot < 0 || endSlot > SLOTS_PER_DAY || firstSlot >= endSlot) {
            throw new IllegalArgumentException("Choose an end time after the start time.");
        }
        List<LocalDate> dates = firstDate.datesUntil(lastDate.plusDays(1)).filter(date -> weekdays.contains(date.getDayOfWeek())).toList();
        if (dates.isEmpty()) {
            throw new IllegalArgumentException("The date range does not include your selected weekdays.");
        }
        // Validate the whole selection before making changes.
        for (LocalDate date : dates) {
            for (int slot = firstSlot; slot < endSlot; slot++) {
                validate(date, slot);
            }
        }
        User viewer = user();
        users.findForMessaging(viewer.getUsername()).orElseThrow();
        List<Availability> existing = slots.findByUserIdAndDateBetween(viewer.getId(), firstDate, lastDate);
        Map<LocalDate, Set<Integer>> existingSlots = existing.stream().collect(Collectors.groupingBy(Availability::getDate, Collectors.mapping(Availability::getSlot, Collectors.toSet())));
        if (available) {
            List<Availability> additions = new ArrayList<>();
            for (LocalDate date : dates) {
                Set<Integer> marked = existingSlots.getOrDefault(date, Set.of());
                for (int slot = firstSlot; slot < endSlot; slot++) {
                    if (!marked.contains(slot)) {
                        additions.add(new Availability(viewer, date, slot));
                    }
                }
            }
            slots.saveAll(additions);
        } else {
            Set<LocalDate> selectedDates = new HashSet<>(dates);
            List<Availability> removals = existing.stream().filter(item -> selectedDates.contains(item.getDate())).filter(item -> item.getSlot() >= firstSlot && item.getSlot() < endSlot).toList();
            slots.deleteAll(removals);
        }
        slots.flush();
        return dates.size();
    }

    public static void validate(LocalDate date, int slot) {
        if (date == null || date.isBefore(LocalDate.now(BookingService.BOOKING_ZONE)) || slot < 0 || slot >= SLOTS_PER_DAY)
            throw new IllegalArgumentException("Choose a current or future date and a 30-minute slot.");
        BookingService.toInstant(date.atStartOfDay().plusMinutes(slot * (long) AvailabilityService.SLOT_MINUTES));
        BookingService.toInstant(date.atStartOfDay().plusMinutes((slot + 1) * (long) AvailabilityService.SLOT_MINUTES));
    }
}

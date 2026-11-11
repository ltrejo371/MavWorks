package com.mavworks.listing;

import com.mavworks.service.BookingService;
import com.mavworks.service.ListingService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;

@Route("listings/:listingId/appointment")
@PageTitle("Schedule Appointment | MavWorks")
@PermitAll
public class AppointmentView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final List<LocalTime> START_TIMES =
            List.of(LocalTime.of(10, 0), LocalTime.of(11, 0), LocalTime.of(15, 0));
    private final ListingService listings;
    private final BookingService bookings;
    private LocalDate today = LocalDate.now(BookingService.BOOKING_ZONE);
    private YearMonth month = YearMonth.from(today);
    private Long listingId;
    private int durationMinutes;
    private List<BookingService.BusySlot> busySlots = List.of();
    private final Map<LocalDate, Set<LocalTime>> requestedTimes = new HashMap<>();
    private String availabilityError;
    private LocalDate selectedDate;
    private LocalTime selectedTime;
    private final Div calendar = new Div();
    private final Div times = new Div();
    private final H2 monthTitle = new H2();
    private final H2 timeTitle = new H2("Choose a date");
    private final Paragraph summary = new Paragraph("Select an available date and time.");
    private final Paragraph feedback = new Paragraph();
    private final Button request = new Button("Request Appointment", event -> requestAppointment());
    private final Button previous = new Button("←");

    public AppointmentView(ListingService listings, BookingService bookings) {
        this.listings = listings;
        this.bookings = bookings;
        addClassNames("mw-page", "appointment-page");
        calendar.addClassName("appointment-calendar");
        times.addClassName("appointment-times");
        monthTitle.getElement().setAttribute("aria-live", "polite");
        summary.getElement().setAttribute("role", "status");
        feedback.getElement().setAttribute("role", "status");
        request.addThemeVariants(ButtonVariant.PRIMARY);
        request.setEnabled(false);
        previous.setAriaLabel("Previous month");
        previous.addClickListener(event -> changeMonth(-1));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        today = LocalDate.now(BookingService.BOOKING_ZONE);
        selectedDate = null;
        selectedTime = null;
        listingId = null;
        busySlots = List.of();
        requestedTimes.clear();
        availabilityError = null;
        month = YearMonth.from(today);
        feedback.setText("");
        add(new Anchor("", "← Back to marketplace"));
        try {
            Long id = Long.valueOf(event.getRouteParameters().get("listingId").orElse(""));
            var result = listings.getListingDetails(id);
            if (result.isEmpty()) {
                add(new Paragraph("Listing not found."));
                return;
            }
            var listing = result.get();
            if (listing.getType() != com.mavworks.model.ListingType.SERVICE
                    || listing.getStatus() != com.mavworks.model.ListingStatus.ACTIVE) {
                add(new Paragraph("This service is not available for booking."));
                return;
            }
            listingId = id;
            durationMinutes = listing.getAppointmentDurationMinutes();
            removeAll();
            add(new Anchor("listings/" + id, "← Back to listing"));
            var heading = new Div(new H1("Schedule an appointment"),
                    new Paragraph(listing.getName() + " · Listed by " + listing.getOwner().getUsername()));
            add(heading);
            var notice = new Paragraph("Choose a time to send a booking request to the provider. "
                    + "All times are Central Time; the provider must approve your request.");
            notice.addClassName("appointment-notice");
            add(notice);
            var next = new Button("→", e -> changeMonth(1));
            next.setAriaLabel("Next month");
            var navigation = new Div(previous, monthTitle, next);
            navigation.addClassName("appointment-month");
            var legend = new Paragraph("Select a weekday to see available appointment times.");
            legend.addClassName("appointment-legend");
            var calendarPanel = new Div(new H2("Availability"), navigation, calendar, legend);
            calendarPanel.addClassNames("mw-panel", "appointment-calendar-panel");
            var timePanel = new Div(timeTitle, new Paragraph(durationMinutes
                    + "-minute appointments · America/Chicago (Central time)"),
                    times, summary, request, feedback, new Anchor("bookings", "View booking requests"));
            timePanel.addClassNames("mw-panel", "appointment-time-panel");
            var grid = new Div(calendarPanel, timePanel);
            grid.addClassName("appointment-grid");
            add(grid);
            renderCalendar();
            renderTimes();
        } catch (NumberFormatException ex) {
            add(new Paragraph("Listing not found."));
        } catch (DataAccessException ex) {
            add(new Paragraph("The listing could not be loaded. Refresh to try again."));
        } catch (AccessDeniedException ex) {
            add(new Paragraph(ex.getMessage()));
        }
    }

    private void changeMonth(int amount) {
        month = month.plusMonths(amount);
        selectedDate = null;
        selectedTime = null;
        busySlots = List.of();
        availabilityError = null;
        feedback.setText("");
        renderCalendar();
        renderTimes();
    }

    private void renderCalendar() {
        calendar.removeAll();
        monthTitle.setText(month.format(MONTH));
        previous.setEnabled(month.isAfter(YearMonth.from(today)));
        for (String day : new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"}) {
            var label = new Span(day);
            label.addClassName("appointment-weekday");
            calendar.add(label);
        }
        for (int i = 1; i < month.atDay(1).getDayOfWeek().getValue(); i++) {
            calendar.add(new Span());
        }
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            boolean available = !date.isBefore(today) && date.getDayOfWeek().getValue() <= 5;
            var button = new Button(String.valueOf(day), event -> {
                selectDate(date);
            });
            button.addClassName("appointment-day");
            button.setEnabled(available);
            button.setAriaLabel(date.format(DATE) + (available ? ", select to view available times" : ", unavailable"));
            button.getElement().setAttribute("aria-pressed", String.valueOf(date.equals(selectedDate)));
            if (date.equals(today)) button.getElement().setAttribute("aria-current", "date");
            if (date.equals(selectedDate)) button.addThemeVariants(ButtonVariant.PRIMARY);
            calendar.add(button);
        }
    }

    private void renderTimes() {
        times.removeAll();
        timeTitle.setText(selectedDate == null ? "Choose a date" : selectedDate.format(DATE));
        request.setEnabled(selectedTime != null && availabilityError == null);
        summary.setText(selectedTime == null ? "Select an available date and time."
                : "Selected: " + selectedDate.format(DATE) + " · "
                        + TIME.format(selectedTime) + " · Central time");
        if (selectedDate == null) {
            times.add(new Paragraph("Select a weekday to see available appointment times."));
            return;
        }
        if (availabilityError != null) {
            times.add(new Paragraph(availabilityError));
            return;
        }
        int availableCount = 0;
        for (LocalTime time : START_TIMES) {
            LocalDateTime start = LocalDateTime.of(selectedDate, time);
            LocalDateTime end = start.plusMinutes(durationMinutes);
            if (requestedTimes.getOrDefault(selectedDate, Set.of()).contains(time)) {
                times.add(new Paragraph(TIME.format(time) + " – " + TIME.format(end) + " · Request sent"));
                continue;
            }
            if (!start.isAfter(LocalDateTime.now(BookingService.BOOKING_ZONE))
                    || !end.toLocalDate().equals(selectedDate)
                    || overlapsApprovedBooking(start, end)) {
                continue;
            }
            availableCount++;
            var slot = new Button(TIME.format(time) + " – " + TIME.format(end.toLocalTime()), event -> {
                selectedTime = time;
                feedback.setText("");
                renderTimes();
            });
            slot.getElement().setAttribute("aria-pressed", String.valueOf(time.equals(selectedTime)));
            if (time.equals(selectedTime)) slot.addThemeVariants(ButtonVariant.PRIMARY);
            times.add(slot);
        }
        if (availableCount == 0) {
            times.add(new Paragraph("No appointment times are available on this date. Choose another date."));
        }
    }

    private void selectDate(LocalDate date) {
        selectedDate = date;
        selectedTime = null;
        busySlots = List.of();
        availabilityError = null;
        feedback.setText("");
        try {
            busySlots = bookings.getApprovedSlots(listingId, date);
        } catch (IllegalArgumentException | AccessDeniedException ex) {
            availabilityError = ex.getMessage();
        } catch (DataAccessException ex) {
            availabilityError = "Availability could not be loaded. Select another date to try again.";
        }
        feedback.setText(availabilityError == null ? "" : availabilityError);
        renderCalendar();
        renderTimes();
    }

    private boolean overlapsApprovedBooking(LocalDateTime start, LocalDateTime end) {
        Instant startAt = start.atZone(BookingService.BOOKING_ZONE).toInstant();
        Instant endAt = end.atZone(BookingService.BOOKING_ZONE).toInstant();
        return busySlots.stream().anyMatch(slot ->
                startAt.isBefore(slot.endAt()) && endAt.isAfter(slot.startAt()));
    }

    private void requestAppointment() {
        if (listingId == null || selectedDate == null || selectedTime == null) return;
        LocalTime requestedTime = selectedTime;
        request.setEnabled(false);
        feedback.setText("");
        try {
            bookings.requestBooking(listingId, selectedDate, requestedTime);
            requestedTimes.computeIfAbsent(selectedDate, ignored -> new HashSet<>()).add(requestedTime);
            selectedTime = null;
            feedback.setText("Your request was sent to the provider. Track its status on the Bookings page.");
            renderTimes();
        } catch (IllegalArgumentException | AccessDeniedException ex) {
            feedback.setText(ex.getMessage());
            renderTimes();
        } catch (DataAccessException ex) {
            feedback.setText("The booking request could not be saved. Please try again.");
            renderTimes();
        }
    }
}

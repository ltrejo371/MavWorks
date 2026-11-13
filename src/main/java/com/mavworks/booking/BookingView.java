package com.mavworks.booking;

import com.mavworks.service.*;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.vaadin.flow.component.details.Details;

@Route("calendar")
@RouteAlias("bookings")
@PageTitle("Calendar")
@Menu(title = "Calendar", icon = "vaadin:calendar", order = 4)
@PermitAll
public class BookingView extends VerticalLayout {
    private final AvailabilityService availability;
    private final BookingService bookings;
    private AvailabilityCalendar calendar;
    private LocalDate date;
    private Set<Integer> marked = Set.of();
    private final Div times = new Div();
    private final Div appointments = new Div();
    private final AvailabilityCalendar.PeriodPicker period = new AvailabilityCalendar.PeriodPicker();
    public BookingView(AvailabilityService availability, BookingService bookings) {
        this.availability = availability; this.bookings = bookings;
        period.addValueChangeListener(e -> { if (date != null) renderTimes(); });
        addClassName("mw-page");
        times.setWidthFull();
        add(new H1("Calendar"));
        try {
            calendar = new AvailabilityCalendar(availability, selected -> {
                try { marked = availability.day(selected); date = selected; renderTimes(); }
                catch (org.springframework.dao.DataAccessException ex) { AvailabilityCalendar.error("Unable to load availability. Please try again later."); }
            });
            AvailabilityEditor editor = new AvailabilityEditor(availability, () -> {
                calendar.reload();
                if (date != null) {
                    marked = availability.day(date);
                    renderTimes();
                }
            });

            Details bulkAvailability = new Details("Set your availability", editor);
            bulkAvailability.setOpened(false);
            bulkAvailability.setWidthFull();

            VerticalLayout dayEditor = new VerticalLayout(new Paragraph("Plans change. Pick a date to adjust individual times."), calendar, times);
            dayEditor.setPadding(false);
            dayEditor.setWidthFull();

            Details adjustments = new Details("Adjust a single day", dayEditor);
            adjustments.setOpened(false);
            adjustments.setWidthFull();

            add(bulkAvailability, adjustments, new H2("Your appointments"), new Button("Refresh appointments", e -> refresh()), appointments);
            refresh();
        } catch (org.springframework.dao.DataAccessException ex) { add(new Paragraph("Unable to load calendar. Please try again later.")); }
    }
    private void renderTimes() {
        times.removeAll();
        Div slotButtons = AvailabilityCalendar.slots(date, marked, false, Set.of(), slot -> {
            try {
                availability.setAvailable(date, slot, !marked.contains(slot));
                marked = availability.day(date);renderTimes();
                calendar.reload();
            } catch (org.springframework.dao.DataAccessException ex) {
                AvailabilityCalendar.error("Unable to save availability changes. Please try again later.");
            } catch (IllegalArgumentException ex) {
                AvailabilityCalendar.error(ex.getMessage());
            }
        }, period.isPm());

        times.add(
                new H2(date.toString()),
                period,
                new Paragraph("Each time marks a 30-minute slot."),
                slotButtons);
    }
    private void refresh() {
        appointments.removeAll();
        try {
            var format = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm z").withZone(BookingService.BOOKING_ZONE);
            var items = bookings.getMyBookings();
            calendar.setBookedDates(items.stream().filter(b -> b.status() == com.mavworks.model.BookingStatus.CONFIRMED)
                    .map(b -> b.startAt().atZone(BookingService.BOOKING_ZONE).toLocalDate()).collect(java.util.stream.Collectors.toSet()));
            if (items.isEmpty()) appointments.add(new Paragraph("No appointments yet. Request one from a listing or its conversation."));
            for (var b : items) {
                var card = new Div(new Paragraph(b.listingName() + " · " + format.format(b.startAt()) + "–"
                        + format.format(b.endAt()) + " · " + b.status()));
                if (b.conversationId() != null) card.add(new Anchor("messages/" + b.conversationId(), "Open conversation"));
                card.add(new AppointmentAvailabilityNotice(b));
                card.add(new AppointmentActions(bookings, b, "calendar", this::refresh));
                if (b.status() == com.mavworks.model.BookingStatus.COMPLETED) card.add(new Paragraph("Appointment completed."));
                appointments.add(card);
            }
        } catch (org.springframework.dao.DataAccessException ex) { AvailabilityCalendar.error("Unable to load appointments. Please try again later."); }
    }
}

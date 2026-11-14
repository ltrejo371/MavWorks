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
    private AppointmentCalendar appointmentCalendar;
    private LocalDate date;
    private Set<Integer> marked = Set.of();
    private final Div times = new Div();
    private final Div appointments = new Div();
    private final AvailabilityCalendar.PeriodPicker period = new AvailabilityCalendar.PeriodPicker();
    public BookingView(AvailabilityService availability, BookingService bookings) {
        this.availability = availability; this.bookings = bookings;
        period.addValueChangeListener(e -> { if (date != null) renderTimes(); });
        addClassNames("mw-page", "calendar-page");
        times.setWidthFull();
        appointments.addClassName("appointment-details-list");
        Button refreshButton = new Button("Refresh", event -> {
            if (appointmentCalendar != null) {
                appointmentCalendar.reload();
                refresh();
            }
        });
        refreshButton.setAriaLabel("Refresh appointments");
        refreshButton.setEnabled(false);
        Div pageHeader = new Div(new H1("Calendar"), refreshButton);
        pageHeader.addClassName("calendar-page-header");
        add(pageHeader);
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

            appointmentCalendar = new AppointmentCalendar(bookings);
            refreshButton.setEnabled(true);

            Details appointmentList = new Details("Appointment details and actions", appointments);
            appointmentList.setOpened(false);
            appointmentList.setWidthFull();

            add(
                    bulkAvailability,
                    adjustments,
                    appointmentCalendar,
                    appointmentList);
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
                Span title = new Span(b.listingName());
                title.addClassName("appointment-detail-title");
                String status = b.status().name().toLowerCase(Locale.ROOT);
                var card = new Div(title, new Paragraph(AppointmentActions.timeRange(b) + " · "
                        + Character.toUpperCase(status.charAt(0)) + status.substring(1)));
                card.addClassName("appointment-detail-card");
                card.add(new AppointmentAvailabilityNotice(b));
                var actions = new AppointmentActions(bookings, b, "calendar", () -> {
                    appointmentCalendar.reload();
                    refresh();
                });
                if (b.conversationId() != null)
                    actions.addComponentAsFirst(new Anchor("messages/" + b.conversationId(), "Open conversation"));
                card.add(actions);
                appointments.add(card);
            }
        } catch (org.springframework.dao.DataAccessException ex) { AvailabilityCalendar.error("Unable to load appointments. Please try again later."); }
    }
}

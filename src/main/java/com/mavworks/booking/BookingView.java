package com.mavworks.booking;

import com.mavworks.model.BookingStatus;
import com.mavworks.service.AvailabilityService;
import com.mavworks.service.BookingService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Route("calendar")
@RouteAlias("bookings")
@PageTitle("Calendar")
@Menu(title = "Calendar", icon = "vaadin:calendar", order = 4)
@PermitAll
@com.vaadin.flow.component.dependency.CssImport("./styles/bookings.css")
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
        this.availability = availability;
        this.bookings = bookings;
        period.addValueChangeListener(event -> {
            if (date != null) renderTimes();
        });
        addClassName("mw-page");
        times.setWidthFull();
        add(new H1("Calendar"),
                new Paragraph("Mark your availability in 30-minute increments. Times are Central Time."));
        try {
            calendar = new AvailabilityCalendar(availability, selected -> {
                try {
                    marked = availability.day(selected);
                    date = selected;
                    renderTimes();
                } catch (org.springframework.dao.DataAccessException ex) {
                    AvailabilityCalendar.error("Unable to load availability. Please try again later.");
                }
            });
            add(calendar, times, new H2("Your appointments"),
                    new Button("Refresh appointments", event -> refresh()), appointments);
            refresh();
        } catch (org.springframework.dao.DataAccessException ex) {
            add(new Paragraph("Unable to load calendar. Please try again later."));
        }
    }

    private void renderTimes() {
        times.removeAll();
        times.add(new H2(date.toString()), period,
                new Paragraph("Each time marks a 30-minute slot."),
                AvailabilityCalendar.slots(date, marked, false, Set.of(), slot -> {
                    try {
                        availability.setAvailable(date, slot, !marked.contains(slot));
                        marked = availability.day(date);
                        renderTimes();
                        calendar.reload();
                    } catch (org.springframework.dao.DataAccessException ex) {
                        AvailabilityCalendar.error("Unable to save availability changes. Please try again later.");
                    } catch (IllegalArgumentException ex) {
                        AvailabilityCalendar.error(ex.getMessage());
                    }
                }, period.isPm()));
    }

    private void refresh() {
        appointments.removeAll();
        try {
            var format = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm z")
                    .withZone(BookingService.BOOKING_ZONE);
            var items = bookings.getMyBookings();
            calendar.setBookedDates(items.stream()
                    .filter(booking -> booking.status() == BookingStatus.CONFIRMED)
                    .map(booking -> booking.startAt().atZone(BookingService.BOOKING_ZONE).toLocalDate())
                    .collect(java.util.stream.Collectors.toSet()));
            if (items.isEmpty()) {
                appointments.add(new Paragraph("No appointments yet. Request one from a listing or its conversation."));
            }
            for (var booking : items) {
                Div card = new Div(new Paragraph(booking.listingName() + " · "
                        + format.format(booking.startAt()) + "–" + format.format(booking.endAt())
                        + " · " + booking.status()));
                if (booking.conversationId() != null) {
                    card.add(new Anchor("messages/" + booking.conversationId(), "Open conversation"));
                }
                card.add(new AppointmentAvailabilityNotice(booking));
                card.add(new AppointmentActions(bookings, booking, "calendar", this::refresh));
                if (booking.status() == BookingStatus.COMPLETED) {
                    card.add(new Paragraph("Appointment completed; listing marked Sold."));
                }
                appointments.add(card);
            }
        } catch (org.springframework.dao.DataAccessException ex) {
            AvailabilityCalendar.error("Unable to load appointments. Please try again later.");
        }
    }
}

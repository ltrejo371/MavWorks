package com.mavworks.listing;

import com.mavworks.booking.AvailabilityCalendar;
import com.mavworks.service.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;

@Route("listings/:listingId/appointment")
@PageTitle("Schedule Appointment | MavWorks")
@PermitAll
public class AppointmentView extends VerticalLayout implements BeforeEnterObserver {
    private final BookingService bookings;
    private final AvailabilityService availability;
    private Long conversationId;
    private Long bookingId;
    private String returnPath;
    private LocalDate date;
    private Set<Integer> available = Set.of();
    private final TreeSet<Integer> selected = new TreeSet<>();

    private final Div times = new Div();
    private AvailabilityCalendar calendar;
    private final AvailabilityCalendar.PeriodPicker period = new AvailabilityCalendar.PeriodPicker();

    private BookingService.BookingSummary original;
    private final Button request = new Button("Request Appointment", event -> submit());

    public AppointmentView(BookingService bookings, AvailabilityService availability) {
        this.bookings = bookings;
        this.availability = availability;
        period.addValueChangeListener(event -> {
            if (date == null) return;
            renderTimes();
        });
        addClassName("mw-page");
        times.setWidthFull();
    }
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        conversationId = null;
        bookingId = null;
        returnPath = null;
        selected.clear();

        date = null;
        available = Set.of();
        times.removeAll();
        request.setEnabled(false);

        request.setText("Request Appointment");
        original = null;
        try {
            Long listingId = Long.valueOf(event.getRouteParameters().get("listingId").orElse(""));
            var params = event.getLocation().getQueryParameters().getParameters();
            var query = params.get("conversation");
            if (params.containsKey("booking")) {
                bookingId = Long.valueOf(params.get("booking").getFirst());
                original = bookings.getAppointment(bookingId);
                if (!listingId.equals(original.listingId())) {
                    throw new IllegalArgumentException("Appointment does not match this listing.");
                }
                if (original.status() != com.mavworks.model.BookingStatus.CONFIRMED
                        || !original.startAt().isAfter(java.time.Instant.now())) {
                    throw new IllegalArgumentException("Only future confirmed appointments can be changed.");
                }
                conversationId = original.conversationId();
                request.setText("Request Reschedule");
            } else {
                conversationId = query == null
                        ? bookings.conversationForListing(listingId)
                        : Long.valueOf(query.getFirst());
            }
            if (!listingId.equals(bookings.listingForConversation(conversationId))) {
                throw new IllegalArgumentException("Conversation does not match this listing.");
            }
            returnPath = query == null ? "listings/" + listingId : "messages/" + conversationId;
            if (original != null) {
                returnPath = params.getOrDefault("from", List.of("messages")).getFirst().equals("calendar")
                        ? "calendar" : "messages/" + conversationId;
            }
            calendar = new AvailabilityCalendar(availability, this::selectDate);
            add(new Anchor(returnPath, "← Back"), new H1(original == null ? "Schedule Appointment" : "Reschedule Appointment"),
                    new Paragraph("Select consecutive 30-minute slots for your appointment. Times are Central Time."),
                    new Paragraph("Green times match your saved availability. You can choose other times without changing your availability."),
                    new Paragraph("The appointment stays Requested until the other participant accepts it in Messages."),
                    calendar, times, request);
            if (original != null) {
                addComponentAtIndex(2, new Paragraph(original.listingName() + " · " + original.buyer() + " · " + original.provider()));
                var start = original.startAt().atZone(BookingService.BOOKING_ZONE);
                date = start.toLocalDate();
                for (Instant slot = original.startAt(); slot.isBefore(original.endAt()); slot = slot.plusSeconds(1800)) {
                    var local = slot.atZone(BookingService.BOOKING_ZONE);
                    if (local.toLocalDate().equals(date)) selected.add(local.getHour() * 2 + local.getMinute() / 30);
                }
                available = availability.day(date);
                calendar.showDate(date);
                period.setValue(start.getHour() >= 12 ? "PM" : "AM");
                renderTimes();
            }
        } catch (IllegalArgumentException | AccessDeniedException ex) {
            add(new Paragraph(ex.getMessage()), new Anchor("messages", "Open a listing conversation"));
        } catch (DataAccessException ex) {
            add(new Paragraph("Unable to load calendar. Please try again later."));
        }
    }

    private void selectDate(LocalDate day) {
        selected.clear();
        request.setEnabled(false);
        times.removeAll();
        date = null;
        try {
            available = availability.day(day);
            date = day;
            renderTimes();
        } catch (DataAccessException ex) {
            AvailabilityCalendar.error("Unable to load availability. Please try again later.");
        }
    }
    private void renderTimes() {

        times.removeAll();
        times.add(new H2(date.toString()), period,
                AvailabilityCalendar.slots(date, selected, false, available, slot -> {
                    TreeSet<Integer> updated = new TreeSet<>(selected);
                    if (!updated.add(slot)) updated.remove(slot);
                    if (!updated.isEmpty() && updated.last() - updated.first() + 1 != updated.size()) {
                        Dialog error = new Dialog();
                        error.setCloseOnOutsideClick(true);
                        Paragraph message = new Paragraph("Appointment times have to be in consecutive slots.");
                        message.getElement().addEventListener("click", event -> error.close());
                        error.add(message);
                        error.open();
                        return;
                    }
                    selected.clear();
                    selected.addAll(updated);
                    renderTimes();
                }, period.isPm()));
        if (!selected.isEmpty()) {
            times.add(new Paragraph("Selected appointment: "
                    + AvailabilityCalendar.timeLabel(selected.first()) + "–"
                    + AvailabilityCalendar.timeLabel(selected.last() + 1)));
        }
        request.setEnabled(!selected.isEmpty());
    }

    private void submit() {
        if (conversationId == null || date == null || selected.isEmpty()) return;
        request.setEnabled(false);
        try {
            if (bookingId == null) bookings.requestAppointment(conversationId, date, selected, Map.of());
            else bookings.reschedule(bookingId, date, selected, Map.of());
            getUI().ifPresent(ui -> ui.navigate(returnPath));
        } catch (IllegalArgumentException | AccessDeniedException ex) {
            AvailabilityCalendar.error(ex.getMessage());
        } catch (AvailabilityService.SaveException ex) {
            AvailabilityCalendar.error("Unable to save availability changes. Please try again later.");
        } catch (DataAccessException ex) {
            AvailabilityCalendar.error("Unable to save the appointment. Please try again later.");
        } finally {
            request.setEnabled(!selected.isEmpty());
        }
    }
}

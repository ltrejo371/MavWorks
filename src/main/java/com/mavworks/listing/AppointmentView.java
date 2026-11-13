package com.mavworks.listing;

import com.mavworks.booking.AvailabilityCalendar;
import com.mavworks.service.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
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
    private String returnPath;
    private LocalDate date;
    private Set<Integer> available = Set.of();
    private final TreeSet<Integer> selected = new TreeSet<>();
    private final Map<LocalDate, Map<Integer, Boolean>> edits = new HashMap<>();
    private final Div times = new Div();
    private AvailabilityCalendar calendar;
    private final AvailabilityCalendar.PeriodPicker period = new AvailabilityCalendar.PeriodPicker();
    private boolean editingAvailability;
    private final Button request = new Button("Request Appointment", event -> submit());

    public AppointmentView(BookingService bookings, AvailabilityService availability) {
        this.bookings = bookings;
        this.availability = availability;
        period.addValueChangeListener(event -> {
            if (date == null) return;
            if (editingAvailability) renderEditor();
            else renderTimes();
        });
        addClassName("mw-page");
        times.setWidthFull();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        conversationId = null;
        returnPath = null;
        selected.clear();
        edits.clear();
        date = null;
        available = Set.of();
        times.removeAll();
        request.setEnabled(false);
        editingAvailability = false;
        try {
            Long listingId = Long.valueOf(event.getRouteParameters().get("listingId").orElse(""));
            var query = event.getLocation().getQueryParameters().getParameters().get("conversation");
            conversationId = query == null
                    ? bookings.conversationForListing(listingId)
                    : Long.valueOf(query.getFirst());
            if (!listingId.equals(bookings.listingForConversation(conversationId))) {
                throw new IllegalArgumentException("Conversation does not match this listing.");
            }
            returnPath = query == null ? "listings/" + listingId : "messages/" + conversationId;
            calendar = new AvailabilityCalendar(availability, this::selectDate);
            add(new Anchor(returnPath, "← Back"), new H1("Schedule Appointment"),
                    new Paragraph("Select consecutive 30-minute slots from your availability. Times are Central Time."),
                    new Paragraph("Availability added here is saved when you request the appointment. Back discards these changes."),
                    calendar, times, request);
        } catch (IllegalArgumentException | AccessDeniedException ex) {
            add(new Paragraph(ex.getMessage()), new Anchor("messages", "Open a listing conversation"));
        } catch (DataAccessException ex) {
            add(new Paragraph("Unable to load calendar. Please try again later."));
        }
    }

    private Set<Integer> availableOn(LocalDate day) {
        var result = new HashSet<>(availability.day(day));
        edits.getOrDefault(day, Map.of()).forEach((slot, value) -> {
            if (value) result.add(slot);
            else result.remove(slot);
        });
        return result;
    }

    private void selectDate(LocalDate day) {
        try {
            Set<Integer> daySlots = availableOn(day);
            if (daySlots.isEmpty()) {
                Dialog prompt = new Dialog();
                prompt.add(new Paragraph("You did not mark this day available, would you like to mark it available?"),
                        new Button("Yes", event -> {
                            prompt.close();
                            editAvailability(day);
                        }),
                        new Button("No", event -> prompt.close()));
                add(prompt);
                prompt.open();
                return;
            }
            date = day;
            available = daySlots;
            selected.clear();
            renderTimes();
        } catch (DataAccessException ex) {
            AvailabilityCalendar.error("Unable to load availability. Please try again later.");
        }
    }

    private void editAvailability(LocalDate day) {
        try {
            date = day;
            available = availableOn(day);
            selected.clear();
            request.setEnabled(false);
            renderEditor();
        } catch (DataAccessException ex) {
            AvailabilityCalendar.error("Unable to load availability. Please try again later.");
        }
    }

    private void renderEditor() {
        editingAvailability = true;
        times.removeAll();
        times.add(new H2("Availability for " + date), period,
                AvailabilityCalendar.slots(date, available, false, Set.of(), slot -> {
                    boolean value = !available.contains(slot);
                    edits.computeIfAbsent(date, ignored -> new HashMap<>()).put(slot, value);
                    var updated = new HashSet<>(available);
                    if (value) updated.add(slot);
                    else updated.remove(slot);
                    available = updated;
                    calendar.setDrafts(edits);
                    renderEditor();
                }, period.isPm()),
                new Button("Choose appointment times", event -> renderTimes()));
    }

    private void renderTimes() {
        editingAvailability = false;
        times.removeAll();
        times.add(new H2(date.toString()), period,
                AvailabilityCalendar.slots(date, selected, true, available, slot -> {
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
                }, period.isPm()),
                new Button("Edit availability", event -> editAvailability(date)));
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
            bookings.requestAppointment(conversationId, date, selected, edits);
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

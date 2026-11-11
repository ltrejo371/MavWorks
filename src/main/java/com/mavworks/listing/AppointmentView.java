package com.mavworks.listing;

import com.mavworks.booking.AvailabilityCalendar;
import com.mavworks.service.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.time.*;
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
    private final Button request = new Button("Request Appointment", e -> submit());
    public AppointmentView(BookingService bookings, AvailabilityService availability) {
        this.bookings = bookings; this.availability = availability;
        period.addValueChangeListener(e -> {
            if (date == null) return;
            if (editingAvailability) renderEditor(); else renderTimes();
        });
        addClassName("mw-page"); times.setWidthFull();
    }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        removeAll(); selected.clear(); edits.clear(); date = null; times.removeAll(); request.setEnabled(false);
        try {
            Long listingId = Long.valueOf(event.getRouteParameters().get("listingId").orElse(""));
            var chat = event.getLocation().getQueryParameters().getParameters().get("conversation");
            conversationId = chat == null ? bookings.conversationForListing(listingId) : Long.valueOf(chat.getFirst());
            if (!listingId.equals(bookings.listingForConversation(conversationId))) throw new IllegalArgumentException("Conversation does not match this listing.");
            returnPath = chat == null ? "listings/" + listingId : "messages/" + conversationId;
            calendar = new AvailabilityCalendar(availability, this::selectDate);
            add(new Anchor(returnPath, "← Back"), new H1("Schedule Appointment"),
                    new Paragraph("Select consecutive 30-minute slots from your availability. Times are Central Time."),
                    new Paragraph("Availability added here is saved when you request the appointment. Back discards these changes."),
                    calendar, times, request);
        } catch (IllegalArgumentException | AccessDeniedException ex) {
            add(new Paragraph(ex.getMessage()), new Anchor("messages", "Open a listing conversation"));
        } catch (DataAccessException ex) { add(new Paragraph("Unable to load calendar. Please try again later.")); }
    }
    private Set<Integer> availableOn(LocalDate day) {
        var result = new HashSet<>(availability.day(day));
        edits.getOrDefault(day, Map.of()).forEach((slot, value) -> { if (value) result.add(slot); else result.remove(slot); });
        return result;
    }
    private void selectDate(LocalDate day) {
        try {
            Set<Integer> daySlots = availableOn(day);
            if (daySlots.isEmpty()) {
                Dialog prompt = new Dialog();
                prompt.add(new Paragraph("You did not mark this day available, would you like to mark it available?"),
                        new Button("Yes", e -> { prompt.close(); editAvailability(day); }), new Button("No", e -> prompt.close()));
                add(prompt);
                prompt.open();
                return;
            }
            date = day; available = daySlots; selected.clear(); renderTimes();
        } catch (DataAccessException ex) { AvailabilityCalendar.error("Unable to load availability. Please try again later."); }
    }
    private void editAvailability(LocalDate day) {
        try {
            Set<Integer> daySlots = availableOn(day);
            date = day; available = daySlots; selected.clear(); request.setEnabled(false);
            renderEditor();
        } catch (DataAccessException ex) { AvailabilityCalendar.error("Unable to load availability. Please try again later."); }
    }
    private void renderEditor() {
        editingAvailability = true;
        times.removeAll();
        times.add(new H2("Availability for " + date), period,
                AvailabilityCalendar.slots(date, available, false, Set.of(), slot -> {
                    boolean value = !available.contains(slot);
                    edits.computeIfAbsent(date, ignored -> new HashMap<>()).put(slot, value);
                    var next = new HashSet<>(available);
                    if (value) next.add(slot); else next.remove(slot);
                    available = next; calendar.setDrafts(edits); renderEditor();
                }, period.isPm()), new Button("Choose appointment times", e -> renderTimes()));
    }
    private void renderTimes() {
        editingAvailability = false;
        times.removeAll();
        times.add(new H2(date.toString()), period, AvailabilityCalendar.slots(date, selected, true, available, slot -> {
            TreeSet<Integer> next = new TreeSet<>(selected);
            if (!next.add(slot)) next.remove(slot);
            if (!next.isEmpty() && next.last() - next.first() + 1 != next.size()) {
                Dialog error = new Dialog(); error.setCloseOnOutsideClick(true);
                var text = new Paragraph("Appointment times have to be in consecutive slots.");
                text.getElement().addEventListener("click", e -> error.close());
                error.add(text); error.open(); return;
            }
            selected.clear(); selected.addAll(next); renderTimes();
        }, period.isPm()), new Button("Edit availability", e -> editAvailability(date)));
        if (!selected.isEmpty()) times.add(new Paragraph("Selected appointment: "
                + AvailabilityCalendar.timeLabel(selected.first()) + "–" + AvailabilityCalendar.timeLabel(selected.last() + 1)));
        request.setEnabled(!selected.isEmpty());
    }
    private void submit() {
        request.setEnabled(false);
        try {
            bookings.requestAppointment(conversationId, date, selected, edits);
            getUI().ifPresent(ui -> ui.navigate(returnPath));
        } catch (IllegalArgumentException | AccessDeniedException ex) { AvailabilityCalendar.error(ex.getMessage()); }
        catch (AvailabilityService.SaveException ex) {
            AvailabilityCalendar.error("Unable to save availability changes. Please try again later.");
        } catch (DataAccessException ex) { AvailabilityCalendar.error("Unable to save the appointment. Please try again later."); }
        finally { request.setEnabled(!selected.isEmpty()); }
    }
}

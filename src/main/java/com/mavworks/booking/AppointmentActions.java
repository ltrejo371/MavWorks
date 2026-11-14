package com.mavworks.booking;

import com.mavworks.model.BookingStatus;
import com.mavworks.service.BookingService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import java.time.Instant;

/** Shared controls for the calendar and listing conversation. */
public class AppointmentActions extends Div {
    public AppointmentActions(BookingService service, BookingService.BookingSummary booking,
            String returnPath, Runnable refresh) {
        addClassName("appointment-action-links");
        if (booking.status() != BookingStatus.CONFIRMED || !booking.startAt().isAfter(Instant.now())) return;
        add(new Anchor("listings/" + booking.listingId() + "/appointment?booking=" + booking.id()
                + "&from=" + (returnPath.equals("calendar") ? "calendar" : "messages"), "Reschedule"));
        Button cancelAction = new Button("Cancel", e -> {
            Dialog dialog = new Dialog();
            dialog.setHeaderTitle("Cancel appointment");
            dialog.add(new Paragraph("You are cancelling this appointment and it will no longer be saved or displayed for you or the other party. Are you sure?"));
            dialog.add(new Button("Yes", confirm -> {
                try {
                    service.cancelAppointment(booking.id());
                    dialog.close();
                    refresh.run();
                } catch (IllegalArgumentException | org.springframework.security.access.AccessDeniedException ex) {
                    AvailabilityCalendar.error(ex.getMessage());
                } catch (org.springframework.dao.DataAccessException ex) {
                    AvailabilityCalendar.error("Unable to cancel the appointment. Please try again later.");
                }
            }), new Button("No", cancel -> dialog.close()));
            add(dialog);
            dialog.addOpenedChangeListener(change -> {
                if (!change.isOpened()) remove(dialog);
            });
            dialog.open();
        });
        cancelAction.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_TERTIARY_INLINE);
        cancelAction.addClassName("appointment-cancel-link");
        add(cancelAction);
    }
    public static String timeRange(BookingService.BookingSummary booking) {
        var start = booking.startAt().atZone(BookingService.BOOKING_ZONE);
        var end = booking.endAt().atZone(BookingService.BOOKING_ZONE);
        var date = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US);
        var time = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US);
        return date.format(start) + " · " + time.format(start) + "–"
                + (start.toLocalDate().equals(end.toLocalDate()) ? "" : date.format(end) + " ")
                + time.format(end) + " CT";
    }
}
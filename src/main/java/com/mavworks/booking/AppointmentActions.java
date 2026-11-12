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
        if (booking.status() != BookingStatus.CONFIRMED || !booking.startAt().isAfter(Instant.now())) return;
        add(new Anchor("listings/" + booking.listingId() + "/appointment?booking=" + booking.id()
                + "&from=" + (returnPath.equals("calendar") ? "calendar" : "messages"), "Reschedule"));
        add(new Button("Cancel", e -> {
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
        }));
    }
}

package com.mavworks.booking;

import com.mavworks.service.BookingService;
import com.vaadin.flow.component.html.Paragraph;

/** Recipient-only advisory; saved availability does not prevent accepting a request. */
public class AppointmentAvailabilityNotice extends Paragraph {
    public AppointmentAvailabilityNotice(BookingService.BookingSummary booking) {
        setVisible(booking.canRespond() && booking.availableForRecipient() != null);
        boolean available = Boolean.TRUE.equals(booking.availableForRecipient());
        setText(available ? "This appointment is within your saved availability."
                : "Warning: this appointment is outside your saved availability for some or all of the requested time. Review the time before accepting.");
        getElement().setAttribute("role", "status");
        getStyle().set("font-weight", "600");
    }
}

package com.mavworks.booking;

import com.mavworks.base.ui.ViewTitle;
import com.mavworks.model.BookingStatus;
import com.mavworks.service.BookingService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;

@Route("bookings")
@PageTitle("Bookings")
@PermitAll
public class BookingView extends VerticalLayout {
    private static final DateTimeFormatter SLOT_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.US)
                    .withZone(BookingService.BOOKING_ZONE);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(BookingService.BOOKING_ZONE);

    private final BookingService service;
    private final ComboBox<BookingService.ServiceOption> servicePicker = new ComboBox<>("Service");
    private final DatePicker datePicker = new DatePicker("Date");
    private final TimePicker timePicker = new TimePicker("Start time");
    private final Paragraph availability = new Paragraph();
    private final Paragraph feedback = new Paragraph();
    private final Div requestList = new Div();
    private final Div incomingList = new Div();

    public BookingView(BookingService service) {
        this.service = service;
        addClassName("mw-page");
        setMaxWidth("68rem");

        servicePicker.setItemLabelGenerator(option -> option.name() + " · " + option.provider()
                + " · " + option.durationMinutes() + " min");
        servicePicker.setPlaceholder("Choose a service");
        servicePicker.setClearButtonVisible(true);
        servicePicker.setWidthFull();

        datePicker.setMin(LocalDate.now(BookingService.BOOKING_ZONE));
        datePicker.setValue(LocalDate.now(BookingService.BOOKING_ZONE).plusDays(1));
        timePicker.setStep(Duration.ofMinutes(15));
        timePicker.setWidthFull();

        availability.getElement().setAttribute("role", "status");
        feedback.getElement().setAttribute("role", "status");
        requestList.setWidthFull();
        incomingList.setWidthFull();

        servicePicker.addValueChangeListener(event -> refreshAvailability());
        datePicker.addValueChangeListener(event -> refreshAvailability());

        Button submit = new Button("Request booking", event -> requestBooking());
        submit.addThemeVariants(ButtonVariant.PRIMARY);
        Button refresh = new Button("Refresh", event -> refreshAll());
        HorizontalLayout formActions = new HorizontalLayout(submit, refresh);
        formActions.setAlignItems(Alignment.END);

        add(new ViewTitle("Bookings"),
                new Paragraph("Choose a service and request a future appointment. Times are shown in Central Time."),
                servicePicker, datePicker, timePicker, availability, formActions, feedback,
                new H2("Requests for your services"), incomingList,
                new H2("Your booking requests"), requestList);

        refreshAll();
    }

    private void requestBooking() {
        feedback.setText("");
        servicePicker.setInvalid(servicePicker.getValue() == null);
        servicePicker.setErrorMessage("Choose a service.");
        datePicker.setInvalid(datePicker.getValue() == null);
        datePicker.setErrorMessage("Choose a date.");
        timePicker.setInvalid(timePicker.getValue() == null);
        timePicker.setErrorMessage("Choose a start time.");
        if (servicePicker.isInvalid() || datePicker.isInvalid() || timePicker.isInvalid()) return;

        try {
            service.requestBooking(servicePicker.getValue().id(), datePicker.getValue(), timePicker.getValue());
            feedback.setText("Your request was sent to the provider.");
            timePicker.clear();
            refreshAvailability();
            refreshBookings();
        } catch (IllegalArgumentException | AccessDeniedException ex) {
            feedback.setText(ex.getMessage());
        } catch (DataAccessException ex) {
            feedback.setText("The booking request could not be saved. Please try again.");
        }
    }

    private void refreshAvailability() {
        availability.setText("");
        if (servicePicker.getValue() == null || datePicker.getValue() == null) return;
        try {
            var slots = service.getApprovedSlots(servicePicker.getValue().id(), datePicker.getValue());
            if (slots.isEmpty()) {
                availability.setText("No approved bookings on this date yet.");
            } else {
                String reserved = slots.stream()
                        .sorted(Comparator.comparing(BookingService.BusySlot::startAt))
                        .map(slot -> TIME_FORMAT.format(slot.startAt()) + "–" + TIME_FORMAT.format(slot.endAt()))
                        .reduce((first, second) -> first + " · " + second).orElse("");
                availability.setText("Already reserved (Central Time): " + reserved);
            }
        } catch (IllegalArgumentException ex) {
            availability.setText(ex.getMessage());
        } catch (DataAccessException ex) {
            availability.setText("Availability could not be loaded. Select Refresh to try again.");
        }
    }

    private void refreshAll() {
        try {
            servicePicker.setItems(service.availableServices());
        } catch (DataAccessException ex) {
            feedback.setText("Services could not be loaded. Please try again.");
        }
        refreshAvailability();
        refreshBookings();
    }

    private void refreshBookings() {
        requestList.removeAll();
        incomingList.removeAll();
        try {
            var bookings = service.getMyBookings();
            var incoming = bookings.stream().filter(BookingService.BookingSummary::currentUserIsProvider).toList();
            var outgoing = bookings.stream().filter(item -> !item.currentUserIsProvider()).toList();
            if (incoming.isEmpty()) incomingList.add(new Paragraph("No requests for your services."));
            if (outgoing.isEmpty()) requestList.add(new Paragraph("You have not requested any bookings."));
            incoming.forEach(item -> incomingList.add(bookingCard(item, true)));
            outgoing.forEach(item -> requestList.add(bookingCard(item, false)));
        } catch (DataAccessException ex) {
            incomingList.add(new Paragraph("Booking requests could not be loaded. Select Refresh to try again."));
        }
    }

    private Div bookingCard(BookingService.BookingSummary booking, boolean incoming) {
        Div card = new Div();
        card.addClassName("mw-panel");
        String person = incoming ? "Requested by " + booking.buyer() : "Provider " + booking.provider();
        Span status = new Span(label(booking.status()));
        status.addClassName("mw-badge");
        Paragraph detail = new Paragraph(booking.listingName() + " · " + person + " · "
                + SLOT_FORMAT.format(booking.startAt()) + "–"
                + TIME_FORMAT.format(booking.endAt()));
        HorizontalLayout actions = new HorizontalLayout();

        if (incoming && booking.status() == BookingStatus.PENDING) {
            Button approve = new Button("Approve", event -> updateBooking(booking.id(), "approve"));
            approve.addThemeVariants(ButtonVariant.PRIMARY);
            Button decline = new Button("Decline", event -> updateBooking(booking.id(), "decline"));
            actions.add(approve, decline);
        } else if (!incoming && (booking.status() == BookingStatus.PENDING
                || booking.status() == BookingStatus.APPROVED)
                && booking.startAt().isAfter(Instant.now())) {
            actions.add(new Button("Cancel request", event -> updateBooking(booking.id(), "cancel")));
        }
        card.add(detail, status, actions);
        return card;
    }

    private void updateBooking(Long id, String action) {
        feedback.setText("");
        try {
            switch (action) {
                case "approve" -> service.approve(id);
                case "decline" -> service.decline(id);
                case "cancel" -> service.cancel(id);
                default -> throw new IllegalArgumentException("Unknown booking action.");
            }
            refreshAvailability();
            refreshBookings();
        } catch (IllegalArgumentException | AccessDeniedException ex) {
            feedback.setText(ex.getMessage());
        } catch (DataAccessException ex) {
            feedback.setText("The booking could not be updated. Please try again.");
        }
    }

    private static String label(BookingStatus status) {
        String lower = status.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}

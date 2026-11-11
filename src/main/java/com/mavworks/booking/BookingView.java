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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;

@Route("bookings")
@PageTitle("Bookings")
@PermitAll
@com.vaadin.flow.component.dependency.CssImport("./styles/bookings.css")
public class BookingView extends VerticalLayout {
    private static final DateTimeFormatter SLOT_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.US)
                    .withZone(BookingService.BOOKING_ZONE);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(BookingService.BOOKING_ZONE);

    private final BookingService service;
    private final ComboBox<BookingService.ServiceOption> servicePicker = new ComboBox<>("Service");
    private final ComboBox<BookingService.ServiceOption> providerServicePicker = new ComboBox<>("Your service");
    private final ComboBox<BookingService.AvailabilityOption> slotPicker = new ComboBox<>("Available time");
    private final DatePicker datePicker = new DatePicker("Date");
    private final DatePicker availabilityDate = new DatePicker("Available date");
    private final TimePicker availabilityTime = new TimePicker("Start time");
    private final Paragraph availability = new Paragraph();
    private final Paragraph feedback = new Paragraph();
    private final Div requestList = new Div();
    private final Div incomingList = new Div();
    private final Div availabilityList = new Div();
    private final Div confirmedCalendar = new Div();
    private YearMonth calendarMonth = YearMonth.from(LocalDate.now(BookingService.BOOKING_ZONE));
    private final H2 calendarTitle = new H2();

    public BookingView(BookingService service) {
        this.service = service;
        addClassName("mw-page");
        setMaxWidth("68rem");

        servicePicker.setItemLabelGenerator(option -> option.name() + " · " + option.provider()
                + " · " + option.durationMinutes() + " min");
        servicePicker.setPlaceholder("Choose a service");
        servicePicker.setClearButtonVisible(true);
        servicePicker.setWidthFull();
        slotPicker.setItemLabelGenerator(slot -> SLOT_FORMAT.format(slot.startAt()) + "–"
                + TIME_FORMAT.format(slot.endAt()) + " Central");
        slotPicker.setPlaceholder("Choose an available appointment time");
        slotPicker.setClearButtonVisible(true);
        slotPicker.setWidthFull();
        providerServicePicker.setItemLabelGenerator(option -> option.name() + " · "
                + option.durationMinutes() + " min");
        providerServicePicker.setPlaceholder("Choose one of your services");
        providerServicePicker.setClearButtonVisible(true);
        providerServicePicker.setWidthFull();

        datePicker.setMin(LocalDate.now(BookingService.BOOKING_ZONE));
        datePicker.setValue(LocalDate.now(BookingService.BOOKING_ZONE).plusDays(1));
        availabilityDate.setMin(LocalDate.now(BookingService.BOOKING_ZONE));
        availabilityDate.setValue(LocalDate.now(BookingService.BOOKING_ZONE).plusDays(1));
        availabilityTime.setStep(Duration.ofMinutes(15));
        availabilityTime.setWidthFull();

        availability.getElement().setAttribute("role", "status");
        feedback.getElement().setAttribute("role", "status");
        requestList.setWidthFull();
        incomingList.setWidthFull();

        servicePicker.addValueChangeListener(event -> refreshAvailability());
        datePicker.addValueChangeListener(event -> refreshAvailability());

        Button submit = new Button("Request booking", event -> requestBooking());
        submit.addThemeVariants(ButtonVariant.PRIMARY);
        Button refresh = new Button("Refresh", event -> refreshAll());
        Button addAvailability = new Button("Add available time", event -> addAvailability());
        addAvailability.addThemeVariants(ButtonVariant.PRIMARY);
        Button previousMonth = new Button("← Previous", event -> changeCalendarMonth(-1));
        Button nextMonth = new Button("Next →", event -> changeCalendarMonth(1));
        Button calendarNow = new Button("This month", event -> {
            calendarMonth = YearMonth.from(LocalDate.now(BookingService.BOOKING_ZONE));
            refreshBookings();
        });
        HorizontalLayout formActions = new HorizontalLayout(submit, refresh);
        formActions.setAlignItems(Alignment.END);

        add(new ViewTitle("Bookings"),
                new Paragraph("Choose a service and request a future appointment. Times are shown in Central Time."),
                servicePicker, datePicker, slotPicker, availability, formActions, feedback,
                new H2("Manage your service availability"),
                new Paragraph("Add appointment times buyers can request. Removing a time with an active request is not allowed."),
                providerServicePicker, availabilityDate, availabilityTime, addAvailability, availabilityList,
                new H2("Requests for your services"), incomingList,
                new H2("Your booking requests"), requestList,
                new H2("Confirmed appointments calendar"),
                new HorizontalLayout(previousMonth, calendarTitle, nextMonth, calendarNow),
                confirmedCalendar);

        providerServicePicker.addValueChangeListener(event -> refreshAvailabilityList());
        refreshAll();
    }

    private void requestBooking() {
        feedback.setText("");
        servicePicker.setInvalid(servicePicker.getValue() == null);
        servicePicker.setErrorMessage("Choose a service.");
        datePicker.setInvalid(datePicker.getValue() == null);
        datePicker.setErrorMessage("Choose a date.");
        slotPicker.setInvalid(slotPicker.getValue() == null);
        slotPicker.setErrorMessage("Choose an available time.");
        if (servicePicker.isInvalid() || datePicker.isInvalid() || slotPicker.isInvalid()) return;

        try {
            LocalTime selected = slotPicker.getValue().startAt()
                    .atZone(BookingService.BOOKING_ZONE).toLocalTime();
            service.requestBooking(servicePicker.getValue().id(), datePicker.getValue(), selected);
            feedback.setText("Your request was sent to the provider.");
            slotPicker.clear();
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
        slotPicker.clear();
        slotPicker.setItems(java.util.List.of());
        if (servicePicker.getValue() == null || datePicker.getValue() == null) return;
        try {
            var slots = service.availableSlots(servicePicker.getValue().id(), datePicker.getValue());
            slotPicker.setItems(slots);
            availability.setText(slots.isEmpty()
                    ? "The provider has no available times on this date."
                    : slots.size() + (slots.size() == 1
                            ? " available time. All times are Central Time."
                            : " available times. All times are Central Time."));
        } catch (IllegalArgumentException ex) {
            availability.setText(ex.getMessage());
        } catch (DataAccessException ex) {
            availability.setText("Availability could not be loaded. Select Refresh to try again.");
        }
    }

    private void refreshAll() {
        try {
            servicePicker.setItems(service.availableServices());
            providerServicePicker.setItems(service.myServices());
        } catch (DataAccessException ex) {
            feedback.setText("Services could not be loaded. Please try again.");
        }
        refreshAvailability();
        refreshAvailabilityList();
        refreshBookings();
    }

    private void refreshBookings() {
        requestList.removeAll();
        incomingList.removeAll();
        confirmedCalendar.removeAll();
        try {
            var bookings = service.getMyBookings();
            var incoming = bookings.stream().filter(BookingService.BookingSummary::currentUserIsProvider).toList();
            var outgoing = bookings.stream().filter(item -> !item.currentUserIsProvider()).toList();
            if (incoming.isEmpty()) incomingList.add(new Paragraph("No requests for your services."));
            if (outgoing.isEmpty()) requestList.add(new Paragraph("You have not requested any bookings."));
            incoming.forEach(item -> incomingList.add(bookingCard(item, true)));
            outgoing.forEach(item -> requestList.add(bookingCard(item, false)));
            renderConfirmedCalendar(bookings);
        } catch (DataAccessException ex) {
            incomingList.add(new Paragraph("Booking requests could not be loaded. Select Refresh to try again."));
        }
    }

    private Div bookingCard(BookingService.BookingSummary booking, boolean incoming) {
        Div card = new Div();
        card.addClassName("mw-panel");
        String person = incoming ? "Requested by " + booking.buyer() : "Provider " + booking.provider();
        Span status = new Span(booking.status() == BookingStatus.APPROVED
                ? (booking.buyerConfirmed() ? "Confirmed" : incoming
                        ? "Awaiting buyer confirmation" : "Provider confirmed")
                : label(booking.status()));
        status.addClassName("mw-badge");
        Paragraph detail = new Paragraph(booking.listingName() + " · " + person + " · "
                + SLOT_FORMAT.format(booking.startAt()) + "–"
                + TIME_FORMAT.format(booking.endAt()));
        HorizontalLayout actions = new HorizontalLayout();

        if (incoming && booking.status() == BookingStatus.PENDING) {
            Button approve = new Button("Confirm request", event -> updateBooking(booking.id(), "approve"));
            approve.addThemeVariants(ButtonVariant.PRIMARY);
            Button decline = new Button("Decline", event -> updateBooking(booking.id(), "decline"));
            actions.add(approve, decline);
        } else if (!incoming && booking.status() == BookingStatus.APPROVED && booking.buyerConfirmed()) {
            actions.add(new Span("Confirmed by both"));
            if (booking.startAt().isAfter(Instant.now())) {
                actions.add(new Button("Cancel appointment", event -> updateBooking(booking.id(), "cancel")));
            }
        } else if (!incoming && booking.status() == BookingStatus.APPROVED) {
            Button confirm = new Button("Confirm appointment", event -> updateBooking(booking.id(), "confirm"));
            confirm.addThemeVariants(ButtonVariant.PRIMARY);
            actions.add(confirm);
            if (booking.startAt().isAfter(Instant.now())) {
                actions.add(new Button("Cancel request", event -> updateBooking(booking.id(), "cancel")));
            }
        } else if (!incoming && (booking.status() == BookingStatus.PENDING
                || booking.status() == BookingStatus.APPROVED)
                && booking.startAt().isAfter(Instant.now())) {
            actions.add(new Button("Cancel request", event -> updateBooking(booking.id(), "cancel")));
        } else if (incoming && booking.status() == BookingStatus.APPROVED) {
            actions.add(new Span(booking.buyerConfirmed()
                    ? "Confirmed by both" : "Waiting for buyer confirmation"));
        }
        card.add(detail, status, actions);
        return card;
    }

    private void updateBooking(Long id, String action) {
        feedback.setText("");
        try {
            switch (action) {
                case "approve" -> service.approve(id);
                case "confirm" -> service.confirmByBuyer(id);
                case "decline" -> service.decline(id);
                case "cancel" -> service.cancel(id);
                default -> throw new IllegalArgumentException("Unknown booking action.");
            }
            refreshAvailability();
            refreshAvailabilityList();
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

    private void addAvailability() {
        feedback.setText("");
        providerServicePicker.setInvalid(providerServicePicker.getValue() == null);
        providerServicePicker.setErrorMessage("Choose one of your services.");
        availabilityDate.setInvalid(availabilityDate.getValue() == null);
        availabilityDate.setErrorMessage("Choose a date.");
        availabilityTime.setInvalid(availabilityTime.getValue() == null);
        availabilityTime.setErrorMessage("Choose a start time.");
        if (providerServicePicker.isInvalid() || availabilityDate.isInvalid()
                || availabilityTime.isInvalid()) return;
        try {
            service.addAvailability(providerServicePicker.getValue().id(),
                    availabilityDate.getValue(), availabilityTime.getValue());
            availabilityTime.clear();
            feedback.setText("Availability added.");
            refreshAvailabilityList();
        } catch (IllegalArgumentException | AccessDeniedException ex) {
            feedback.setText(ex.getMessage());
        } catch (DataAccessException ex) {
            feedback.setText("Availability could not be saved. Please try again.");
        }
    }

    private void refreshAvailabilityList() {
        availabilityList.removeAll();
        try {
            var slots = service.myAvailability();
            if (providerServicePicker.getValue() != null) {
                slots = slots.stream().filter(slot ->
                        slot.listingId().equals(providerServicePicker.getValue().id())).toList();
            }
            if (slots.isEmpty()) {
                availabilityList.add(new Paragraph("No future availability is set for this service."));
                return;
            }
            slots.forEach(slot -> {
                String time = SLOT_FORMAT.format(slot.startAt()) + "–" + TIME_FORMAT.format(slot.endAt());
                Button remove = new Button("Remove", event -> removeAvailability(slot.id()));
                remove.setEnabled(!slot.reserved());
                availabilityList.add(new Div(new Span(slot.listingName() + " · " + time
                        + (slot.reserved() ? " · Booking requested/reserved" : "")), remove));
            });
        } catch (DataAccessException ex) {
            availabilityList.add(new Paragraph("Availability could not be loaded. Select Refresh to try again."));
        }
    }

    private void removeAvailability(Long id) {
        try {
            service.removeAvailability(id);
            feedback.setText("Availability removed.");
            refreshAvailabilityList();
        } catch (IllegalArgumentException | AccessDeniedException ex) {
            feedback.setText(ex.getMessage());
        } catch (DataAccessException ex) {
            feedback.setText("Availability could not be removed. Please try again.");
        }
    }

    private void changeCalendarMonth(int amount) {
        calendarMonth = calendarMonth.plusMonths(amount);
        refreshBookings();
    }

    private void renderConfirmedCalendar(java.util.List<BookingService.BookingSummary> bookings) {
        calendarTitle.setText(calendarMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)));
        var days = new Div();
        days.addClassName("booking-calendar-grid");
        for (String weekday : new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"}) {
            Span day = new Span(weekday);
            day.addClassName("booking-calendar-weekday");
            days.add(day);
        }
        for (int i = 1; i < calendarMonth.atDay(1).getDayOfWeek().getValue(); i++) {
            days.add(new Span());
        }
        Map<LocalDate, java.util.List<BookingService.BookingSummary>> confirmed = bookings.stream()
                .filter(item -> item.status() == BookingStatus.APPROVED && item.buyerConfirmed())
                .filter(item -> YearMonth.from(item.startAt().atZone(BookingService.BOOKING_ZONE)).equals(calendarMonth))
                .collect(Collectors.groupingBy(item -> item.startAt().atZone(BookingService.BOOKING_ZONE).toLocalDate()));
        for (int day = 1; day <= calendarMonth.lengthOfMonth(); day++) {
            LocalDate date = calendarMonth.atDay(day);
            Div cell = new Div(new H2(Integer.toString(day)));
            cell.addClassName("booking-calendar-day");
            confirmed.getOrDefault(date, java.util.List.of()).stream()
                    .sorted(Comparator.comparing(BookingService.BookingSummary::startAt))
                    .forEach(item -> cell.add(new Paragraph(TIME_FORMAT.format(item.startAt())
                            + " · " + item.listingName())));
            days.add(cell);
        }
        confirmedCalendar.add(days);
        if (confirmed.isEmpty()) {
            confirmedCalendar.add(new Paragraph("No confirmed appointments this month."));
        }
    }
}

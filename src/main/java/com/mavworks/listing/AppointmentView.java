package com.mavworks.listing;

import com.mavworks.service.ListingService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Interactive layout preview only; no availability or booking records are persisted. */
@Route("listings/:listingId/appointment")
@PageTitle("Schedule Appointment | MavWorks")
@PermitAll
public class AppointmentView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US);
    private final ListingService listings;
    private final LocalDate today = LocalDate.now(ZoneId.of("America/Chicago"));
    private YearMonth month = YearMonth.from(today);
    private LocalDate selectedDate;
    private String selectedTime;
    private final Div calendar = new Div();
    private final Div times = new Div();
    private final H2 monthTitle = new H2();
    private final H2 timeTitle = new H2("Choose a date");
    private final Paragraph summary = new Paragraph("Select an available date and time to preview your request.");
    private final Paragraph feedback = new Paragraph();
    private final Button request = new Button("Request Appointment");
    private final Button previous = new Button("←");

    public AppointmentView(ListingService listings) {
        this.listings = listings;
        addClassNames("mw-page", "appointment-page");
        calendar.addClassName("appointment-calendar");
        times.addClassName("appointment-times");
        monthTitle.getElement().setAttribute("aria-live", "polite");
        summary.getElement().setAttribute("role", "status");
        feedback.getElement().setAttribute("role", "status");
        request.addThemeVariants(ButtonVariant.PRIMARY);
        request.setEnabled(false);
        request.addClickListener(event -> feedback.setText(
                "Preview only: " + selectedDate.format(DATE) + ", " + selectedTime
                        + " (America/Chicago). No request was sent and no appointment was booked."));
        previous.setAriaLabel("Previous month");
        previous.addClickListener(event -> changeMonth(-1));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        selectedDate = null;
        selectedTime = null;
        month = YearMonth.from(today);
        feedback.setText("");
        add(new Anchor("", "← Back to marketplace"));
        try {
            Long id = Long.valueOf(event.getRouteParameters().get("listingId").orElse(""));
            var result = listings.getListingDetails(id);
            if (result.isEmpty()) {
                add(new Paragraph("Listing not found."));
                return;
            }
            removeAll();
            var listing = result.get();
            add(new Anchor("listings/" + id, "← Back to listing"));
            var heading = new Div(new H1("Schedule an appointment"),
                    new Paragraph(listing.getName() + " · Listed by " + listing.getOwner().getUsername()));
            add(heading);
            var notice = new Paragraph("Scheduling preview — availability and times below are examples. "
                    + "Requests are not sent to the provider or saved yet.");
            notice.addClassName("appointment-notice");
            add(notice);
            var next = new Button("→", e -> changeMonth(1));
            next.setAriaLabel("Next month");
            var navigation = new Div(previous, monthTitle, next);
            navigation.addClassName("appointment-month");
            var legend = new Paragraph("Blue outline: sample availability · Gray: unavailable · Solid blue: selected");
            legend.addClassName("appointment-legend");
            var calendarPanel = new Div(new H2("Availability"), navigation, calendar, legend);
            calendarPanel.addClassNames("mw-panel", "appointment-calendar-panel");
            var timePanel = new Div(timeTitle, new Paragraph("One-hour appointments · America/Chicago (Central time)"),
                    times, summary, request, feedback);
            timePanel.addClassNames("mw-panel", "appointment-time-panel");
            var grid = new Div(calendarPanel, timePanel);
            grid.addClassName("appointment-grid");
            add(grid);
            renderCalendar();
            renderTimes();
        } catch (NumberFormatException ex) {
            add(new Paragraph("Listing not found."));
        } catch (org.springframework.dao.DataAccessException ex) {
            add(new Paragraph("The listing could not be loaded. Refresh to try again."));
        }
    }

    private void changeMonth(int amount) {
        month = month.plusMonths(amount);
        selectedDate = null;
        selectedTime = null;
        feedback.setText("");
        renderCalendar();
        renderTimes();
    }

    private void renderCalendar() {
        calendar.removeAll();
        monthTitle.setText(month.format(MONTH));
        previous.setEnabled(month.isAfter(YearMonth.from(today)));
        for (String day : new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"}) {
            var label = new Span(day);
            label.addClassName("appointment-weekday");
            calendar.add(label);
        }
        for (int i = 1; i < month.atDay(1).getDayOfWeek().getValue(); i++) {
            calendar.add(new Span());
        }
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            boolean available = !date.isBefore(today) && date.getDayOfWeek().getValue() <= 5;
            var button = new Button(String.valueOf(day), event -> {
                selectedDate = date;
                selectedTime = null;
                feedback.setText("");
                renderCalendar();
                renderTimes();
            });
            button.addClassName("appointment-day");
            button.setEnabled(available);
            button.setAriaLabel(date.format(DATE) + (available ? ", sample times available" : ", unavailable"));
            button.getElement().setAttribute("aria-pressed", String.valueOf(date.equals(selectedDate)));
            if (date.equals(today)) button.getElement().setAttribute("aria-current", "date");
            if (date.equals(selectedDate)) button.addThemeVariants(ButtonVariant.PRIMARY);
            calendar.add(button);
        }
    }

    private void renderTimes() {
        times.removeAll();
        timeTitle.setText(selectedDate == null ? "Choose a date" : selectedDate.format(DATE));
        request.setEnabled(selectedTime != null);
        summary.setText(selectedTime == null ? "Select an available date and time to preview your request."
                : "Selected: " + selectedDate.format(DATE) + " · " + selectedTime + " · Central time");
        if (selectedDate == null) {
            times.add(new Paragraph("Select a highlighted weekday to see sample appointment times."));
            return;
        }
        for (String time : new String[]{"10:00 AM – 11:00 AM", "11:00 AM – 12:00 PM", "3:00 PM – 4:00 PM"}) {
            var slot = new Button(time, event -> {
                selectedTime = time;
                feedback.setText("");
                renderTimes();
            });
            slot.getElement().setAttribute("aria-pressed", String.valueOf(time.equals(selectedTime)));
            if (time.equals(selectedTime)) slot.addThemeVariants(ButtonVariant.PRIMARY);
            times.add(slot);
        }
    }
}

package com.mavworks.booking;

import com.mavworks.service.BookingService;
import com.mavworks.service.BookingService.BookingSummary;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.dao.DataAccessException;
import com.vaadin.flow.component.dependency.CssImport;

@CssImport("./styles/appointment-calendar.css")

public class AppointmentCalendar extends VerticalLayout {
    private static final DateTimeFormatter MONTH_FORMAT =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a", Locale.US)
                    .withZone(BookingService.BOOKING_ZONE);

    private final BookingService bookings;
    private final H2 heading = new H2();
    private final Div grid = new Div();
    private final Paragraph feedback = new Paragraph();
    private final Set<LocalDate> expandedDays = new HashSet<>();

    private YearMonth month =
            YearMonth.now(BookingService.BOOKING_ZONE);

    public AppointmentCalendar(BookingService bookings) {
        this.bookings = bookings;

        setPadding(false);
        setSpacing(false);
        setWidthFull();
        addClassName("appointment-month-view");

        Button previous = new Button("Previous", event -> move(-1));
        Button today = new Button("Today", event -> {
            month = YearMonth.now(BookingService.BOOKING_ZONE);
            expandedDays.clear();
            reload();
        });
        Button next = new Button("Next", event -> move(1));

        Div navigation = new Div(previous, today, next);
        navigation.addClassName("appointment-calendar-navigation");

        Span timezone = new Span("Central Time");
        timezone.addClassName("appointment-calendar-timezone");
        Div monthTitle = new Div(heading, timezone);
        monthTitle.addClassName("appointment-calendar-month-title");
        Div header = new Div(monthTitle, navigation);
        header.addClassName("appointment-calendar-header");

        grid.addClassName("appointment-calendar-grid");
        feedback.getElement().setAttribute("role", "status");
        feedback.addClassName("appointment-calendar-feedback");

        Div scroll = new Div(grid);
        scroll.addClassName("appointment-calendar-scroll");
        scroll.setWidthFull();

        add(header, scroll, feedback);
        reload();
    }

    private void move(int months) {
        month = month.plusMonths(months);
        expandedDays.clear();
        reload();
    }

    public void reload() {
        heading.setText(month.format(MONTH_FORMAT));
        grid.removeAll();
        feedback.setText("");

        LocalDate firstDate = month.atDay(1);

        // Begin on Monday, including dates from the previous month.
        LocalDate gridStart = firstDate.minusDays(
                firstDate.getDayOfWeek().getValue() - 1);

        // Six weeks keeps the calendar height consistent between months.
        LocalDate gridEnd = gridStart.plusDays(42);

        try {
            List<BookingSummary> items =
                    bookings.getMyBookingsInRange(gridStart, gridEnd);

            Map<LocalDate, List<BookingSummary>> appointmentsByDay =
                    groupByDay(items, gridStart, gridEnd);

            render(gridStart, appointmentsByDay);

            boolean hasAppointmentsThisMonth =
                    appointmentsByDay.keySet().stream()
                            .anyMatch(day -> YearMonth.from(day).equals(month));

            if (!hasAppointmentsThisMonth) {
                feedback.setText("No appointments this month.");
            }
        } catch (DataAccessException ex) {
            feedback.setText(
                    "Unable to load appointments. Select Refresh to try again.");
        }
    }

    private Map<LocalDate, List<BookingSummary>> groupByDay(
            List<BookingSummary> items,
            LocalDate gridStart,
            LocalDate gridEnd) {

        Map<LocalDate, List<BookingSummary>> result = new HashMap<>();

        for (BookingSummary booking : items) {
            LocalDate first = booking.startAt()
                    .atZone(BookingService.BOOKING_ZONE).toLocalDate();

            // An appointment ending at midnight belongs to the preceding day.
            LocalDate last = booking.endAt().minusNanos(1)
                    .atZone(BookingService.BOOKING_ZONE).toLocalDate();

            LocalDate day = first.isBefore(gridStart) ? gridStart : first;

            while (!day.isAfter(last) && day.isBefore(gridEnd)) {
                result.computeIfAbsent(day, ignored -> new ArrayList<>())
                        .add(booking);
                day = day.plusDays(1);
            }
        }

        result.values().forEach(dayItems ->
                dayItems.sort(Comparator.comparing(BookingSummary::startAt)
                        .thenComparing(BookingSummary::id)));

        return result;
    }

    private void render(
            LocalDate gridStart,
            Map<LocalDate, List<BookingSummary>> appointmentsByDay) {

        grid.removeAll();

        for (String name : List.of(
                "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")) {
            Span weekday = new Span(name);
            weekday.addClassName("appointment-calendar-weekday");
            grid.add(weekday);
        }

        LocalDate today = LocalDate.now(BookingService.BOOKING_ZONE);

        for (int offset = 0; offset < 42; offset++) {
            LocalDate day = gridStart.plusDays(offset);
            Div cell = new Div();
            cell.addClassName("appointment-calendar-day");

            if (!YearMonth.from(day).equals(month)) {
                cell.addClassName("outside-month");
            }
            if (day.equals(today)) {
                cell.addClassName("is-today");
            }

            Span dateLabel = new Span(String.valueOf(day.getDayOfMonth()));
            dateLabel.addClassName("appointment-calendar-date");
            dateLabel.getElement().setAttribute("aria-label", day.toString());
            dateLabel.getElement().setAttribute("data-full-date",
                    day.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)));
            cell.add(dateLabel);

            List<BookingSummary> dayItems =
                    appointmentsByDay.getOrDefault(day, List.of());

            if (!dayItems.isEmpty()) cell.addClassName("has-appointments");
            int limit = expandedDays.contains(day) ? dayItems.size() : 3;

            dayItems.stream().limit(limit)
                    .forEach(booking -> cell.add(appointmentRow(booking, day)));

            if (dayItems.size() > 3) {
                boolean expanded = expandedDays.contains(day);
                Button more = new Button(
                        expanded ? "Show less" : "+" + (dayItems.size() - 3) + " more",
                        event -> {
                            if (expanded) expandedDays.remove(day);
                            else expandedDays.add(day);
                            render(gridStart, appointmentsByDay);
                        });
                more.addClassName("appointment-calendar-more");
                more.getElement().setAttribute(
                        "aria-expanded", String.valueOf(expanded));
                cell.add(more);
            }

            grid.add(cell);
        }
    }

    private Div appointmentRow(BookingSummary booking, LocalDate day) {
        LocalDate startDay = booking.startAt()
                .atZone(BookingService.BOOKING_ZONE).toLocalDate();

        String time = startDay.isBefore(day)
                ? "Continues" : TIME_FORMAT.format(booking.startAt());

        String label = time + " · " + booking.listingName();

        Div row = new Div();
        row.addClassNames("appointment-calendar-entry",
                "status-" + booking.status().name().toLowerCase(Locale.ROOT));

        if (booking.conversationId() != null) {
            row.add(new Anchor(
                    "messages/" + booking.conversationId(), label));
        } else {
            row.add(new Span(label));
        }

        String status = booking.status().name().toLowerCase(Locale.ROOT);
        Span statusLabel = new Span(
                Character.toUpperCase(status.charAt(0)) + status.substring(1));
        statusLabel.addClassName("appointment-calendar-status");

        row.add(statusLabel);
        return row;
    }
}
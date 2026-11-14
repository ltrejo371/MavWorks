package com.mavworks;

import com.mavworks.booking.AppointmentCalendar;
import com.mavworks.model.BookingStatus;
import com.mavworks.service.BookingService;
import com.mavworks.service.BookingService.BookingSummary;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppointmentCalendarTest {
    private final BookingService bookings = mock(BookingService.class);
    private final YearMonth month = YearMonth.now(BookingService.BOOKING_ZONE);
    private final LocalDate day = month.atDay(15);

    @BeforeEach void setup() { UI.setCurrent(new UI()); }
    @AfterEach void cleanup() { UI.setCurrent(null); }

    @Test void navigationLoadsSixWeeksAndReturnsToCurrentMonth() {
        var calendar = new AppointmentCalendar(bookings);
        assertRange(month);
        assertEquals(42, components(calendar).filter(c -> c.getElement().getClassList().contains("appointment-calendar-day")).count());
        assertTrue(calendar.getElement().getTextRecursively().contains("No appointments this month."));
        button(calendar, "Previous").click();
        assertRange(month.minusMonths(1));
        button(calendar, "Next").click();
        button(calendar, "Next").click();
        assertRange(month.plusMonths(1));
        button(calendar, "Today").click();
        verify(bookings, times(3)).getMyBookingsInRange(start(month), start(month).plusDays(42));
    }

    @Test void busyDayOpensOrderedDialogWithoutExpandingDesktopCells() {
        when(bookings.getMyBookingsInRange(any(), any())).thenReturn(List.of(
                item(4, day.atTime(16, 0), day.atTime(17, 0)),
                item(2, day.atTime(9, 0), day.atTime(10, 0)),
                item(1, day.atTime(9, 0), day.atTime(10, 0)),
                item(3, day.atTime(12, 0), day.atTime(13, 0))));
        var calendar = new AppointmentCalendar(bookings);
        assertEquals(List.of("9:00 AM · Service 1", "9:00 AM · Service 2",
                "12:00 PM · Service 3", "4:00 PM · Service 4"), labels(calendar));
        assertEquals(2, components(calendar).filter(c ->
                c.getElement().getClassList().contains("appointment-calendar-mobile-extra")).count());
        button(calendar, "+2 more").click();
        var dialog = components(calendar).filter(com.vaadin.flow.component.dialog.Dialog.class::isInstance)
                .map(com.vaadin.flow.component.dialog.Dialog.class::cast).findFirst().orElseThrow();
        assertTrue(dialog.isOpened());
        assertEquals(List.of("9:00 AM · Service 1", "9:00 AM · Service 2",
                "12:00 PM · Service 3", "4:00 PM · Service 4"), labels(dialog));
        assertTrue(dialog.getElement().getTextRecursively().contains("Buyer: buyer · Provider: provider"));
        dialog.close();
        assertFalse(dialog.isOpened());
        assertEquals(4, labels(calendar).size());        verify(bookings, times(1)).getMyBookingsInRange(any(), any());
    }

    @Test void overnightEntryContinuesButMidnightEndDoesNotAddAnotherDay() {
        when(bookings.getMyBookingsInRange(any(), any())).thenReturn(List.of(
                item(1, day.atTime(23, 30), day.plusDays(1).atTime(0, 30)),
                item(2, day.atTime(23, 0), day.plusDays(1).atStartOfDay())));
        var calendar = new AppointmentCalendar(bookings);
        assertEquals(List.of("11:00 PM · Service 2", "11:30 PM · Service 1", "Continues · Service 1"), labels(calendar));
        assertTrue(components(calendar).filter(Anchor.class::isInstance).map(Anchor.class::cast)
                .allMatch(a -> a.getHref().equals("messages/50")));
        assertEquals(3, components(calendar).filter(c -> c instanceof Span s && s.getText().endsWith(" · Confirmed")).count());
    }

    @Test void failedRefreshClearsOldEntriesAndCanRecover() {
        when(bookings.getMyBookingsInRange(any(), any()))
                .thenReturn(List.of(item(1, day.atTime(9, 0), day.atTime(10, 0))))
                .thenThrow(new DataAccessResourceFailureException("offline"))
                .thenReturn(List.of());
        var calendar = new AppointmentCalendar(bookings);
        assertEquals(1, labels(calendar).size());
        calendar.reload();
        assertTrue(labels(calendar).isEmpty());
        assertTrue(calendar.getElement().getTextRecursively().contains("Unable to load appointments."));
        calendar.reload();
        assertFalse(calendar.getElement().getTextRecursively().contains("Unable to load appointments."));
        assertTrue(calendar.getElement().getTextRecursively().contains("No appointments this month."));
    }

    private BookingSummary item(long id, LocalDateTime start, LocalDateTime end) {
        return new BookingSummary(id, 10L, 50L, "Service " + id, "buyer", "provider", false,
                start.atZone(BookingService.BOOKING_ZONE).toInstant(),
                end.atZone(BookingService.BOOKING_ZONE).toInstant(), BookingStatus.CONFIRMED, null);
    }
    private LocalDate start(YearMonth value) {
        LocalDate first = value.atDay(1);
        return first.minusDays(first.getDayOfWeek().getValue() - 1);
    }
    private void assertRange(YearMonth value) {
        verify(bookings).getMyBookingsInRange(start(value), start(value).plusDays(42));
    }
    private List<String> labels(Component root) {
        return components(root).filter(Anchor.class::isInstance).map(Anchor.class::cast).map(a -> a.getElement().getAttribute("aria-label")).toList();
    }
    private Button button(Component root, String text) {
        return components(root).filter(c -> c instanceof Button b && b.getText().equals(text))
                .map(Button.class::cast).findFirst().orElseThrow();
    }
    private Stream<Component> components(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::components));
    }
}
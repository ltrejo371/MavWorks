package com.mavworks;

import com.mavworks.booking.*;
import com.mavworks.listing.AppointmentView;
import com.mavworks.service.*;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.router.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Paragraph;
import org.springframework.dao.DataAccessResourceFailureException;

class CalendarViewTest {
    private final AvailabilityService availability = mock(AvailabilityService.class);
    private final BookingService bookings = mock(BookingService.class);
    private final LocalDate date = LocalDate.now(BookingService.BOOKING_ZONE).plusDays(1);
    private UI ui;
    @BeforeEach void setup() { ui = new UI(); UI.setCurrent(ui); }
    @AfterEach void cleanup() { UI.setCurrent(null); }

    @Test void availabilityShows24SlotsPerPeriodAndKeepsPmSelectedAfterSaving() {
        when(availability.day(date)).thenReturn(new HashSet<>(Set.of(24)), new HashSet<>());
        var view = new BookingView(availability, bookings); ui.add(view);
        chooseDate(view);
        period(view).setValue("PM");
        var slot = button(view, "12:00 PM–12:30 PM");
        assertEquals("true", slot.getElement().getAttribute("aria-pressed"));
        assertEquals(24, components(view).filter(c -> c instanceof Button b && b.getAriaLabel().orElse("").contains("–")).count());
        slot.click();
        verify(availability).setAvailable(date, 24, false);
        assertEquals("false", button(view, "12:00 PM–12:30 PM").getElement().getAttribute("aria-pressed"));
    }
    @Test void schedulingDisplaysSavedSlotsAndRejectsGaps() {
        when(availability.day(date)).thenReturn(Set.of(24, 25, 26));
        var view = schedule(); chooseDate(view);
        period(view).setValue("PM");
        assertEquals(3, components(view).filter(c -> c instanceof Button b && b.getAriaLabel().orElse("").contains("–")).count());
        button(view, "12:00 PM–12:30 PM").click();
        button(view, "1:00 PM–1:30 PM").click();
        assertEquals("false", button(view, "1:00 PM–1:30 PM").getElement().getAttribute("aria-pressed"));
        assertTrue(button(view, "Request Appointment").isEnabled());
        verify(bookings, never()).requestAppointment(any(), any(), any(), any());
    }
    @Test void unavailableDateNoKeepsAppointmentUnselected() {
        when(availability.day(date)).thenReturn(Set.of());
        var view = schedule(); chooseDate(view);
        button(ui, "No").click();
        assertFalse(button(view, "Request Appointment").isEnabled());
        verify(availability, never()).setAvailable(any(), anyInt(), anyBoolean());
    }
    @Test void periodsStartAtMidnightAndNoonAndEndWithElevenThirty() {
        var view = new BookingView(availability, bookings); ui.add(view); chooseDate(view);
        assertNotNull(button(view, "12:00 AM–12:30 AM"));
        assertNotNull(button(view, "11:30 AM–12:00 PM"));
        assertFalse(components(view).anyMatch(c -> c instanceof Button b && b.getAriaLabel().orElse("").equals("12:00 PM–12:30 PM")));
        period(view).setValue("PM");
        assertNotNull(button(view, "12:00 PM–12:30 PM"));
        assertNotNull(button(view, "11:30 PM–12:00 AM"));
        assertEquals(24, components(view).filter(c -> c instanceof Button b && b.getAriaLabel().orElse("").contains("–")).count());
    }
    @Test void appointmentSelectionSurvivesPeriodSwitchAndCanCrossNoon() {
        when(availability.day(date)).thenReturn(Set.of(23, 24));
        var view = schedule(); chooseDate(view);
        button(view, "11:30 AM–12:00 PM").click();
        period(view).setValue("PM");
        button(view, "12:00 PM–12:30 PM").click();
        period(view).setValue("AM");
        assertEquals("true", button(view, "11:30 AM–12:00 PM").getElement().getAttribute("aria-pressed"));
        assertTrue(components(view).anyMatch(c -> c instanceof com.vaadin.flow.component.html.Paragraph p
                && p.getText().equals("Selected appointment: 11:30 AM–12:30 PM")));
    }
    @Test void availabilityEditsDuringSchedulingRemainDrafts() {
        when(availability.day(date)).thenReturn(Set.of());
        var view = schedule(); chooseDate(view);
        button(ui, "Yes").click();
        period(view).setValue("PM");
        button(view, "12:00 PM–12:30 PM").click();
        button(view, "Choose appointment times").click();
        button(view, "12:00 PM–12:30 PM").click();
        assertTrue(button(view, "Request Appointment").isEnabled());
        verify(availability, never()).setAvailable(any(), anyInt(), anyBoolean());
        verify(bookings, never()).requestAppointment(any(), any(), any(), any());
    }
    @Test void reschedulePrefillsOriginalDateSlotsAndReturnLink() {
        when(bookings.listingForConversation(3L)).thenReturn(10L);
        when(bookings.getAppointment(7L)).thenReturn(new BookingService.BookingSummary(7L, 10L, 3L,
                "Tutoring", "buyer", "provider", false, BookingService.toInstant(date.atTime(12, 0)),
                BookingService.toInstant(date.atTime(13, 0)), com.mavworks.model.BookingStatus.CONFIRMED, null));
        var view = new AppointmentView(bookings, availability); ui.add(view);
        var event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters("listingId", "10"));
        when(event.getLocation()).thenReturn(new Location("listings/10/appointment?booking=7&from=calendar"));
        view.beforeEnter(event);
        assertEquals("true", button(view, "12:00 PM–12:30 PM").getElement().getAttribute("aria-pressed"));
        assertEquals("true", button(view, "12:30 PM–1:00 PM").getElement().getAttribute("aria-pressed"));
        assertTrue(button(view, "Request Reschedule").isEnabled());
        assertTrue(components(view).anyMatch(c -> c instanceof com.vaadin.flow.component.html.Anchor a && a.getHref().equals("calendar")));
        verify(bookings, never()).reschedule(any(), any(), any(), any());
    }
    @Test void cancellationRequiresYesAndNoLeavesAppointmentAlone() {
        var booking = new BookingService.BookingSummary(7L, 10L, 3L, "Tutoring", "buyer", "provider", false,
                BookingService.toInstant(date.atTime(12, 0)), BookingService.toInstant(date.atTime(13, 0)),
                com.mavworks.model.BookingStatus.CONFIRMED, null);
        Runnable refresh = mock(Runnable.class);
        var actions = new AppointmentActions(bookings, booking, "calendar", refresh); ui.add(actions);
        button(actions, "Cancel").click(); button(ui, "No").click();
        verify(bookings, never()).cancelAppointment(any());
        button(actions, "Cancel").click(); button(ui, "Yes").click();
        verify(bookings).cancelAppointment(7L); verify(refresh).run();
    }
    @Test
    void availabilityEditorsStartCollapsedWithBulkFirst() {
        var view = new BookingView(availability, bookings);
        ui.add(view);
        List<Component> children = view.getChildren().toList();
        List<Details> sections = children.stream().filter(Details.class::isInstance).map(Details.class::cast).toList();
        assertEquals(2, sections.size());
        assertFalse(sections.get(0).isOpened());
        assertFalse(sections.get(1).isOpened());
        assertTrue(components(sections.get(0)).anyMatch(AvailabilityEditor.class::isInstance));
        assertTrue(components(sections.get(1)).anyMatch(AvailabilityCalendar.class::isInstance));
    }

    @Test
    void bulkEditorAppliesBothActionsAndRefreshesAfterSaving() {
        Runnable refresh = mock(Runnable.class);
        var editor = new AvailabilityEditor(availability, refresh);
        ui.add(editor);
        configureBulkEditor(editor);

        Set<DayOfWeek> weekdays = Set.of(date.getDayOfWeek());
        when(availability.setAvailableRange(date, date, weekdays, 18, 34, true)).thenReturn(1);
        when(availability.setAvailableRange(date, date, weekdays, 18, 34, false)).thenReturn(1);
        button(editor, "Mark available").click();
        verify(availability).setAvailableRange(date, date, weekdays, 18, 34, true);
        assertTrue(hasParagraph(editor, "Marked selected times available across 1 day."));
        button(editor, "Mark unavailable").click();
        verify(availability).setAvailableRange(date, date, weekdays, 18, 34, false);
        verify(refresh, times(2)).run();
        assertTrue(hasParagraph(editor, "Marked selected times unavailable across 1 day."));
    }

    @Test
    void bulkEditorShowsValidationAndSaveErrorsWithoutRefreshing() {
        Runnable refresh = mock(Runnable.class);
        var editor = new AvailabilityEditor(availability, refresh);
        ui.add(editor);
        configureBulkEditor(editor);
        when(availability.setAvailableRange(date, date, Set.of(date.getDayOfWeek()), 18, 34, true)).thenThrow(new IllegalArgumentException("Choose at least one weekday.")).thenThrow(new DataAccessResourceFailureException("offline"));
        button(editor, "Mark available").click();
        assertTrue(hasParagraph(editor, "Choose at least one weekday."));
        button(editor, "Mark available").click();
        assertTrue(hasParagraph(editor, "Unable to save availability changes. Please try again later."));
        verifyNoInteractions(refresh);
    }

    @Test
    void bulkEditorDistinguishesSavedChangesFromRefreshFailure() {
        Runnable refresh = mock(Runnable.class);
        var editor = new AvailabilityEditor(availability, refresh);
        ui.add(editor);
        configureBulkEditor(editor);
        when(availability.setAvailableRange(date, date, Set.of(date.getDayOfWeek()), 18, 34, true)).thenReturn(1);
        doThrow(new DataAccessResourceFailureException("offline")).when(refresh).run();
        button(editor, "Mark available").click();
        assertTrue(components(editor).filter(Paragraph.class::isInstance).map(Paragraph.class::cast).anyMatch(p -> p.getText().contains("Changes were saved") && p.getText().contains("could not refresh")));
    }

    @SuppressWarnings("unchecked")
    private void configureBulkEditor(AvailabilityEditor editor) {
        components(editor).filter(DatePicker.class::isInstance).map(DatePicker.class::cast).forEach(picker -> picker.setValue(date));
        CheckboxGroup<DayOfWeek> weekdays = (CheckboxGroup<DayOfWeek>) components(editor).filter(CheckboxGroup.class::isInstance).findFirst().orElseThrow();
        weekdays.setValue(Set.of(date.getDayOfWeek()));
    }

    private boolean hasParagraph(Component root, String text) {
        return components(root).filter(Paragraph.class::isInstance).map(Paragraph.class::cast).anyMatch(p -> p.getText().equals(text));
    }
    private AppointmentView schedule() {
        when(bookings.listingForConversation(3L)).thenReturn(10L);
        var view = new AppointmentView(bookings, availability); ui.add(view);
        var event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters("listingId", "10"));
        when(event.getLocation()).thenReturn(new Location("listings/10/appointment?conversation=3"));
        view.beforeEnter(event); return view;
    }
    private void chooseDate(Component view) {
        components(view).filter(Details.class::isInstance).map(Details.class::cast).forEach(details -> details.setOpened(true));
        if (!YearMonth.from(date).equals(YearMonth.now(BookingService.BOOKING_ZONE))) button(view, "Next month").click();
        components(view).filter(c -> c instanceof Button b && b.getAriaLabel().orElse("").startsWith(date.toString()))
                .map(Button.class::cast).findFirst().orElseThrow().click();
    }
    private AvailabilityCalendar.PeriodPicker period(Component root) {
        return components(root).filter(AvailabilityCalendar.PeriodPicker.class::isInstance)
                .map(AvailabilityCalendar.PeriodPicker.class::cast).findFirst().orElseThrow();
    }
    private Button button(Component root, String text) {
        return components(root).filter(c -> c instanceof Button b && (b.getText().equals(text) || b.getAriaLabel().orElse("").equals(text))).map(Button.class::cast).findFirst().orElseThrow();
    }
    private Stream<Component> components(Component root) { return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::components)); }
}

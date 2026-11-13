package com.mavworks.booking;

import com.mavworks.service.*;
import com.vaadin.flow.component.button.*;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/** Month calendar with highlighted availability and half-hour time selection. */
public class AvailabilityCalendar extends VerticalLayout {
    private final AvailabilityService service;
    private final Consumer<LocalDate> onDate;
    private YearMonth month = YearMonth.now(BookingService.BOOKING_ZONE);
    private Map<LocalDate, Set<Integer>> available = Map.of();
    private Map<LocalDate, Map<Integer, Boolean>> drafts = Map.of();
    private Set<LocalDate> bookedDates = Set.of();
    private LocalDate selected;
    private final Div days = new Div();
    private final H2 title = new H2();
    public AvailabilityCalendar(AvailabilityService service, Consumer<LocalDate> onDate) {
        this.service = service; this.onDate = onDate;
        days.getStyle().set("display", "grid").set("grid-template-columns", "repeat(7, minmax(0, 1fr))").set("gap", "4px");
        days.setWidthFull();
        Div navigation = new Div(new Button("Previous month", e -> move(-1)), new Button("Next month", e -> move(1)));
        navigation.getStyle().set("display", "flex").set("gap", "8px").set("flex-wrap", "wrap");
        title.getStyle().set("margin", "0");
        Div header = new Div(title, navigation);
        header.setWidthFull();
        header.getStyle().set("display", "flex").set("align-items", "center").set("justify-content", "space-between").set("gap", "12px").set("flex-wrap", "wrap");

        add(header, days);        reload();
    }
    private void move(int delta) {
        YearMonth previous = month;
        month = month.plusMonths(delta);
        try { reload(); }
        catch (org.springframework.dao.DataAccessException ex) {
            month = previous;
            error("Unable to load calendar. Please try again later.");
        }
    }
    public void reload() { available = service.month(month); render(); }
    public void showDate(LocalDate date) { selected = date; month = YearMonth.from(date); reload(); }
    public void setDrafts(Map<LocalDate, Map<Integer, Boolean>> drafts) { this.drafts = drafts; render(); }
    public void setBookedDates(Set<LocalDate> dates) { bookedDates = dates; render(); }
    private void render() {
        title.setText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)));
        days.removeAll();
        for (String day : List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")) days.add(new Span(day));
        for (int i = 1; i < month.atDay(1).getDayOfWeek().getValue(); i++) days.add(new Span());
        for (int i = 1; i <= month.lengthOfMonth(); i++) {
            LocalDate date = month.atDay(i);
            Set<Integer> effective = new HashSet<>(available.getOrDefault(date, Set.of()));
            drafts.getOrDefault(date, Map.of()).forEach((slot, value) -> { if (value) effective.add(slot); else effective.remove(slot); });
            boolean marked = !effective.isEmpty();
            boolean booked = bookedDates.contains(date);
            Button day = new Button(i + (marked ? " •" : "") + (booked ? " ✓" : ""), e -> { selected = date; render(); onDate.accept(date); });
            day.setEnabled(!date.isBefore(LocalDate.now(BookingService.BOOKING_ZONE)));
            day.setAriaLabel(date + (marked ? ", available" : ", unavailable") + (booked ? ", confirmed appointment" : ""));
            day.getElement().setAttribute("aria-pressed", String.valueOf(date.equals(selected)));
            if (marked) day.addThemeVariants(ButtonVariant.SUCCESS);
            if (date.equals(selected)) day.addThemeVariants(ButtonVariant.PRIMARY);
            days.add(day);
        }
    }
    public static class PeriodPicker extends RadioButtonGroup<String> {
        public PeriodPicker() {
            setLabel("Time of day"); setItems("AM", "PM"); setValue("AM");
        }
        public boolean isPm() { return "PM".equals(getValue()); }
    }
    public static String timeLabel(int slot) {
        return LocalTime.MIDNIGHT.plusMinutes(slot * (long) AvailabilityService.SLOT_MINUTES)
                .format(DateTimeFormatter.ofPattern("h:mm a", Locale.US));
    }
    public static String slotLabel(int slot) {
        return timeLabel(slot) + "–" + timeLabel(slot + 1);
    }
    public static Div slots(LocalDate date, Set<Integer> selected, boolean onlyAvailable, Set<Integer> available,
            Consumer<Integer> clicked, boolean pm) {
        Div bar = new Div();
        bar.getStyle().set("display", "grid").set("grid-template-columns", "repeat(auto-fit, minmax(90px, 1fr))").set("gap", "6px");
        bar.setWidthFull();
        int halfDay = AvailabilityService.SLOTS_PER_DAY / 2;
        int first = pm ? halfDay : 0;
        for (int i = first; i < first + halfDay; i++) {
            int slot = i;
            if (onlyAvailable && !available.contains(slot)) continue;
            String startTime = LocalTime.MIDNIGHT.plusMinutes(slot * (long) AvailabilityService.SLOT_MINUTES)
                    .format(DateTimeFormatter.ofPattern("h:mm", Locale.US));
            Button button = new Button(startTime, e -> clicked.accept(slot));
            button.setAriaLabel(slotLabel(slot));
            button.setTooltipText(slotLabel(slot));
            boolean marked = selected.contains(slot);
            button.getElement().setAttribute("aria-pressed", String.valueOf(marked));
            if (marked) button.addThemeVariants(ButtonVariant.PRIMARY);
            try { AvailabilityService.validate(date, slot); }
            catch (IllegalArgumentException ex) { button.setEnabled(false); button.setTooltipText(ex.getMessage()); }
            bar.add(button);
        }
        if (bar.getComponentCount() == 0) bar.add(new Paragraph("No available times in the " + (pm ? "PM" : "AM") + ". Choose the other period or edit your availability."));
        return bar;
    }
    public static void error(String message) {
        var dialog = new com.vaadin.flow.component.dialog.Dialog();
        Button close = new Button("×", e -> dialog.close()); close.setAriaLabel("Close error");
        dialog.add(new Paragraph(message), close);
        dialog.open();
    }
}

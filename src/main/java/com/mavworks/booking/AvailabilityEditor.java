package com.mavworks.booking;

import com.mavworks.service.AvailabilityService;
import com.mavworks.service.BookingService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.stream.IntStream;
import org.springframework.dao.DataAccessException;

public class AvailabilityEditor extends VerticalLayout {
    private final AvailabilityService availability;
    private final Runnable onSaved;

    private final DatePicker firstDate = new DatePicker("Start date");
    private final DatePicker lastDate = new DatePicker("End date");
    private final CheckboxGroup<DayOfWeek> weekdays = new CheckboxGroup<>();
    private final Select<Integer> startTime = new Select<>();
    private final Select<Integer> endTime = new Select<>();
    private final Paragraph feedback = new Paragraph();

    public AvailabilityEditor(
            AvailabilityService availability,
            Runnable onSaved) {
        this.availability = availability;
        this.onSaved = onSaved;

        setPadding(false);
        setWidthFull();

        LocalDate today = LocalDate.now(BookingService.BOOKING_ZONE);
        firstDate.setMin(today);
        lastDate.setMin(today);
        firstDate.setValue(today);
        lastDate.setValue(today.plusDays(27));

        weekdays.setLabel("Days of the week");
        weekdays.setItems(DayOfWeek.values());
        weekdays.setItemLabelGenerator(day ->
                day.getDisplayName(TextStyle.SHORT, Locale.US));

        startTime.setLabel("From");
        startTime.setItems(IntStream.range(0, 48).boxed().toList());
        startTime.setItemLabelGenerator(AvailabilityEditor::timeLabel);
        startTime.setValue(18);

        endTime.setLabel("Until");
        endTime.setItems(IntStream.rangeClosed(1, 48).boxed().toList());
        endTime.setItemLabelGenerator(AvailabilityEditor::timeLabel);
        endTime.setValue(34);

        FormLayout fields = new FormLayout(
                firstDate, lastDate, startTime, endTime);
        fields.setWidthFull();
        fields.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2));

        Button markAvailable =
                new Button("Mark available", event -> apply(true));
        markAvailable.addThemeVariants(ButtonVariant.PRIMARY);

        Button markUnavailable =
                new Button("Mark unavailable", event -> apply(false));

        Div actions = new Div(markAvailable, markUnavailable);
        actions.getStyle()
                .set("display", "flex")
                .set("gap", "8px")
                .set("flex-wrap", "wrap");

        feedback.getElement().setAttribute("role", "status");
        feedback.getElement().setAttribute("aria-live", "polite");

        add(
                new Paragraph(
                        "Make time for your next meetup! Choose when you're free "
                                + "to meet with buyers or service providers."),
                fields,
                weekdays,
                new Paragraph(
                        "Times are Central Time. Apply up to 90 days at once. "
                                + "Only the selected times change; appointments stay unchanged."),
                actions,
                feedback);
    }

    private void apply(boolean available) {
        feedback.setText("");

        if (startTime.getValue() == null || endTime.getValue() == null) {
            feedback.setText("Choose a start and end time.");
            return;
        }

        int days;
        try {
            days = availability.setAvailableRange(
                    firstDate.getValue(),
                    lastDate.getValue(),
                    weekdays.getValue(),
                    startTime.getValue(),
                    endTime.getValue(),
                    available);
        } catch (IllegalArgumentException ex) {
            feedback.setText(ex.getMessage());
            return;
        } catch (DataAccessException ex) {
            feedback.setText(
                    "Unable to save availability changes. Please try again later.");
            return;
        }

        String result = "Marked selected times "
                + (available ? "available" : "unavailable")
                + " across " + days + (days == 1 ? " day." : " days.");

        feedback.setText(result);

        try {
            onSaved.run();
        } catch (DataAccessException ex) {
            feedback.setText(
                    result + " Changes were saved, but the calendar could not "
                            + "refresh. Reload the page to see them.");
        }
    }

    private static String timeLabel(int slot) {
        if (slot == 48) {
            return "12:00 AM (end of day)";
        }

        return LocalTime.MIDNIGHT
                .plusMinutes(slot * (long) AvailabilityService.SLOT_MINUTES)
                .format(DateTimeFormatter.ofPattern("h:mm a", Locale.US));
    }
}
package com.mavworks;

import com.mavworks.admin.ReportDetailsView;
import com.mavworks.model.*;
import com.mavworks.service.*;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.*;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportDetailsViewTest {
    private UI ui;
    @BeforeEach void setup() { ui = new UI(); UI.setCurrent(ui); }
    @AfterEach void clear() { UI.setCurrent(null); }

    @Test void selectingDecisionWithoutSavingDoesNotUpdateReport() {
        var reports = mock(ReportService.class);
        when(reports.details(1L)).thenReturn(detail(ReportStatus.PENDING));
        var view = view(reports);
        decision(view).setValue(ReportStatus.INVALID);
        verify(reports, never()).validateReport(any(), any(), any(), any());
    }

    @Test void failedSaveRetainsSelectionAndRetryDisplaysArchivedDecision() {
        var reports = mock(ReportService.class);
        when(reports.details(1L)).thenReturn(detail(ReportStatus.PENDING), detail(ReportStatus.INVALID));
        doThrow(new DataAccessResourceFailureException("offline")).doNothing()
                .when(reports).validateReport(1L, ReportStatus.INVALID, null, "No evidence");
        var view = view(reports);
        var decision = decision(view);
        var note = (TextArea) components(view).filter(TextArea.class::isInstance).findFirst().orElseThrow();
        decision.setValue(ReportStatus.INVALID);
        note.setValue("No evidence");
        button(view, "Save decision").click();
        assertEquals(ReportStatus.INVALID, decision.getValue());
        assertEquals("No evidence", note.getValue());
        button(view, "Save decision").click();
        verify(reports, times(2)).validateReport(1L, ReportStatus.INVALID, null, "No evidence");
        assertTrue(components(view).anyMatch(c -> c instanceof Paragraph p && p.getText().equals("Archived decision: INVALID")));
        assertFalse(components(view).anyMatch(TextArea.class::isInstance));
    }

    @Test void conflictingDecisionShowsErrorAndRequiresReload() {
        var reports = mock(ReportService.class);
        when(reports.details(1L)).thenReturn(detail(ReportStatus.PENDING));
        doThrow(new IllegalStateException("This report has already been reviewed. Reload to see the saved decision."))
                .when(reports).validateReport(1L, ReportStatus.INVALID, null, "");
        var view = view(reports);
        decision(view).setValue(ReportStatus.INVALID);
        button(view, "Save decision").click();
        assertFalse(button(view, "Save decision").isVisible());
        assertFalse(decision(view).isEnabled());
        assertTrue(components(view).anyMatch(c -> c instanceof Paragraph p && p.getText().contains("already been reviewed")));
    }

    private ReportService.ReportDetails detail(ReportStatus status) {
        return new ReportService.ReportDetails(new ReportService.ReportSummary(1L, "reporter", "target",
                ReportCategory.SPAM, "Original reason", status, Instant.now()), 0, false, false,
                status == ReportStatus.PENDING ? null : "admin", status == ReportStatus.PENDING ? null : Instant.now(), null, "No evidence");
    }
    private ReportDetailsView view(ReportService reports) {
        var terms = mock(TermsOfServiceService.class);
        when(terms.getCurrentTerms()).thenReturn(Optional.empty());
        var view = new ReportDetailsView(reports, terms);
        var event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters("reportId", "1"));
        view.beforeEnter(event);
        return view;
    }
    @SuppressWarnings("unchecked")
    private ComboBox<ReportStatus> decision(Component root) {
        return (ComboBox<ReportStatus>) components(root).filter(c -> c instanceof ComboBox<?> b && b.getLabel().equals("Decision")).findFirst().orElseThrow();
    }
    private Button button(Component root, String label) {
        return components(root).filter(c -> c instanceof Button b && b.getText().equals(label)).map(Button.class::cast).findFirst().orElseThrow();
    }
    private Stream<Component> components(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::components));
    }
}

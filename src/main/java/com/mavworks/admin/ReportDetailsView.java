package com.mavworks.admin;

import com.mavworks.base.ui.ViewTitle;
import com.mavworks.model.*;
import com.mavworks.service.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;

@Route("admin/reports/:reportId")
@PageTitle("Review report")
@RolesAllowed("ADMIN")
public class ReportDetailsView extends VerticalLayout implements BeforeEnterObserver {
    private final ReportService reports;
    private final TermsOfServiceService terms;
    private Long id;

    public ReportDetailsView(ReportService reports, TermsOfServiceService terms) {
        this.reports = reports;
        this.terms = terms;
        addClassNames("mw-page", "mw-form-page", "report-detail-page");
        setMaxWidth("52rem");
    }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        try {
            id = Long.valueOf(event.getRouteParameters().get("reportId").orElse(""));
            load();
        } catch (NumberFormatException ex) {
            removeAll();
            add(new Paragraph("Report not found."), new Anchor("admin/reports", "Back to reports"));
        }
    }
    private void load() {
        removeAll();
        add(new Anchor("admin/reports", "Back to reports"));
        try {
            var detail = reports.details(id);
            var report = detail.report();
            add(new ViewTitle("Report #" + id), new Paragraph("Category: " + report.category().getLabel()),
                    new Paragraph("Reported account: " + report.reportedUser()),
                    new Paragraph("Reporter: " + report.reporter()), new Paragraph("Status: " + report.status()),
                    new Paragraph("Other valid reports for this account: " + detail.priorValidReports()));
            Paragraph reason = new Paragraph(report.reason().isBlank() ? "No additional reason provided." : report.reason());
            reason.getStyle().set("white-space", "pre-wrap").set("overflow-wrap", "anywhere");
            add(reason);
            terms.getCurrentTerms().ifPresentOrElse(t -> {
                Paragraph content = new Paragraph(t.getContent());
                content.getStyle().set("white-space", "pre-wrap");
                add(new Details("Current terms of service — " + t.getVersion(), content));
            }, () -> add(new Paragraph("No current terms of service are available in the database.")));
            if (report.status() != ReportStatus.PENDING) {
                add(new Paragraph("Archived decision: " + report.status()),
                        new Paragraph("Reviewed by " + (detail.reviewedBy() == null ? "Deleted administrator" : detail.reviewedBy())
                                + " at " + detail.reviewedAt()),
                        new Paragraph("Action: " + (detail.action() == null ? "None" : detail.action())),
                        new Paragraph(detail.explanation() == null ? "" : detail.explanation()));
                return;
            }
            if (detail.accountDeleted()) add(new Paragraph("This account was deleted. A warning or ban cannot be issued."));
            if (detail.accountBanned()) add(new Paragraph("This account is already banned."));
            ComboBox<ReportStatus> decision = new ComboBox<>("Decision");
            decision.setItems(ReportStatus.VALID, ReportStatus.INVALID);
            decision.setRequiredIndicatorVisible(true);
            ComboBox<ModerationAction> action = new ComboBox<>("Action for a valid report");
            action.setItems(ModerationAction.values());
            action.setVisible(false);
            TextArea note = new TextArea("Explanation");
            note.setMaxLength(2000);
            note.setWidthFull();
            note.setHelperText("For a valid report, explain the violation to the reported user. Do not include the reporter's private information.");
            decision.addValueChangeListener(e -> {
                boolean valid = e.getValue() == ReportStatus.VALID;
                action.setVisible(valid);
                action.setRequiredIndicatorVisible(valid);
                note.setRequiredIndicatorVisible(valid);
                if (!valid) action.clear();
            });
            Paragraph error = new Paragraph();
            error.getElement().setAttribute("role", "alert");
            Button save = new Button("Save decision");
            save.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.PRIMARY);
            save.setDisableOnClick(true);
            save.addClickListener(e -> {
                try {
                    reports.validateReport(id, decision.getValue(), action.getValue(), note.getValue());
                    Notification.show("The report decision was saved and archived.");
                    load();
                } catch (IllegalStateException ex) {
                    error.setText(ex.getMessage());
                    save.setVisible(false);
                    decision.setEnabled(false);
                    action.setEnabled(false);
                    note.setEnabled(false);
                } catch (IllegalArgumentException | AccessDeniedException ex) {
                    error.setText(ex.getMessage());
                } catch (DataAccessException ex) {
                    error.setText("The decision could not be saved. Your entries are still here; try again.");
                } finally { save.setEnabled(true); }
            });
            add(decision, action, note, error, save, new Button("Reload report", e -> load()));
        } catch (IllegalArgumentException ex) {
            add(new Paragraph(ex.getMessage()));
        } catch (DataAccessException ex) {
            add(new Paragraph("The report could not be loaded."), new Button("Try again", e -> load()));
        }
    }
}

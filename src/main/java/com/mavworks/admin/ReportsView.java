package com.mavworks.admin;

import com.mavworks.base.ui.ViewTitle;
import com.mavworks.service.ReportService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.mavworks.model.ReportStatus;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.dao.DataAccessException;

@Route("admin/reports")
@PageTitle("Reports")
@Menu(order = 3, title = "Reports")
@RolesAllowed("ADMIN")
public class ReportsView extends VerticalLayout implements BeforeEnterObserver {
    private final ReportService reports;
    private final VerticalLayout results = new VerticalLayout();
    private final Button previous = new Button("Previous");
    private final Button next = new Button("Next");
    private int page;
    private final ComboBox<ReportStatus> status = new ComboBox<>("Status");

    public ReportsView(ReportService reports) {
        this.reports = reports;
        addClassNames("mw-page", "reports-page");
        status.setItems(ReportStatus.values());
        status.setItemLabelGenerator(value -> value == ReportStatus.PENDING ? "Pending" : value + " (archived)");
        status.setValue(ReportStatus.PENDING);
        status.addValueChangeListener(e -> { if (e.getValue() != null) { page = 0; load(); } });
        previous.addClickListener(e -> { if (page > 0) { page--; load(); } });
        next.addClickListener(e -> { page++; load(); });
        add(new ViewTitle("Reports"), status, new Button("Refresh", e -> load()), results, previous, next);
        results.setPadding(false);
    }
    @Override public void beforeEnter(BeforeEnterEvent event) { page = 0; load(); }
    private void load() {
        results.removeAll();
        try {
            var data = reports.reports(status.getValue(), page);
            previous.setEnabled(data.hasPrevious());
            next.setEnabled(data.hasNext());
            if (data.isEmpty()) results.add(new Paragraph("No reports with this status on this page."));
            for (var report : data) {
                Div card = new Div();
                card.addClassNames("mw-panel", "report-card");
                card.getStyle().set("padding", "1rem").set("border-bottom", "1px solid #ddd");
                Paragraph reason = new Paragraph(report.reason().isBlank() ? "No additional reason provided." : report.reason());
                reason.getStyle().set("white-space", "pre-wrap").set("overflow-wrap", "anywhere");
                H2 title = new H2();
                title.add(new Anchor("admin/reports/" + report.id(), "Report #" + report.id() + " · " + report.category().getLabel()));
                card.add(title,
                        new Paragraph("Reported account: " + report.reportedUser()),
                        new Paragraph("Reporter: " + report.reporter()),
                        new Paragraph("Status: " + report.status() + " · " + report.createdAt()), reason);
                results.add(card);
            }
        } catch (DataAccessException ex) {
            next.setEnabled(false);
            results.add(new Paragraph("Reports could not be loaded. Select Refresh to try again."));
        }
    }
}

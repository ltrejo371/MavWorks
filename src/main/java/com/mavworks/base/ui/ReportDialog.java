package com.mavworks.base.ui;

import com.mavworks.model.ReportCategory;
import com.mavworks.service.ReportService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextArea;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;

public class ReportDialog extends Dialog {
    public ReportDialog(Long targetId, ReportService reports) {
        setHeaderTitle("Report account");
        setWidth("28rem");
        ComboBox<ReportCategory> category = new ComboBox<>("Category");
        category.setItems(ReportCategory.values());
        category.setItemLabelGenerator(ReportCategory::getLabel);
        category.setRequiredIndicatorVisible(true);
        category.setPlaceholder("Select a category");
        category.setHelperText("A category is required before you can submit your report.");
        category.setWidthFull();
        TextArea reason = new TextArea("Reason (optional)");
        reason.setMaxLength(4000);
        reason.setWidthFull();
        Paragraph error = new Paragraph();
        error.getElement().setAttribute("role", "alert");
        Button submit = new Button("Submit");
        submit.setEnabled(false);
        submit.setDisableOnClick(true);
        category.addValueChangeListener(e -> {
            submit.setEnabled(e.getValue() != null);
            category.setInvalid(e.getValue() == null);
            category.setErrorMessage("Select a category before submitting.");
        });
        submit.addClickListener(e -> {
            if (category.getValue() == null) {
                category.setInvalid(true);
                category.setErrorMessage("Select a category before submitting.");
                return;
            }
            try {
                reports.reportUser(targetId, category.getValue(), reason.getValue());
                close();
                Notification.show("Your report was submitted to the administrators for review.",
                        6000, Notification.Position.MIDDLE);
            } catch (IllegalArgumentException | AccessDeniedException ex) {
                error.setText(ex.getMessage());
            } catch (DataAccessException ex) {
                error.setText("Your report could not be submitted. Select Try again.");
                submit.setText("Try again");
            } finally { submit.setEnabled(category.getValue() != null); }
        });
        add(new Paragraph("Choose the category that best describes the issue. Reporting does not block the account."),
                category, reason, error);
        getFooter().add(new Button("Cancel", e -> close()), submit);
        addOpenedChangeListener(e -> {
            if (!e.isOpened()) { category.clear(); reason.clear(); }
        });
    }
}

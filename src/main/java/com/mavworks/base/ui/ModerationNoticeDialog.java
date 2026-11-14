package com.mavworks.base.ui;

import com.mavworks.service.ModerationNoticeService.Notice;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
import java.util.List;

public class ModerationNoticeDialog extends Dialog {
    public ModerationNoticeDialog(List<Notice> notices, boolean banned, String appealEmail, Runnable acknowledge) {
        setHeaderTitle(banned ? "Your account has been banned" : "Account warning");
        setCloseOnEsc(false);
        setCloseOnOutsideClick(false);
        setWidth("32rem");
        for (var notice : notices) {
            add(new H3("Warning #" + notice.warningNumber() + " — " + notice.offense()));
            var explanation = new Paragraph(notice.explanation());
            explanation.getStyle().set("white-space", "pre-wrap").set("overflow-wrap", "anywhere");
            add(explanation);
        }
        if (banned) {
            add(new Paragraph("You have been banned from MavWorks as a result of these violations. You cannot access your account."),
                    new Paragraph("To appeal this decision, email:"), new Anchor("mailto:" + appealEmail, appealEmail));
        }
        var error = new Paragraph();
        error.getElement().setAttribute("role", "alert");
        add(error);
        getFooter().add(new Button("I understand", event -> {
            try { acknowledge.run(); close(); }
            catch (org.springframework.dao.DataAccessException ex) { error.setText("Could not save your acknowledgment. Please try again."); }
        }));
    }
}

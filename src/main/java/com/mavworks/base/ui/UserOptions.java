package com.mavworks.base.ui;

import com.mavworks.service.BlockService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;

/** A username opens account options; changes require an explicit confirmation or submission. */
public class UserOptions extends Button {
    public UserOptions(Long userId, String username, BlockService blocks,
            com.mavworks.service.ReportService reports, Runnable afterBlock) {
        super(username);
        getElement().setAttribute("aria-label", "Options for " + username);
        getStyle().set("background", "transparent").set("padding", "0").set("color", "inherit");
        addClickListener(event -> {
            Dialog options = new Dialog();
            options.setHeaderTitle("User options");
            options.add(new Anchor("profiles/" + userId, "View profile"));
            options.getFooter().add(new Button("Cancel", click -> options.close()),
                    new Button("Report", click -> {
                        options.close();
                        new ReportDialog(userId, reports).open();
                    }),
                    new Button("Block", click -> {
                        options.close();
                        confirmation(userId, blocks, afterBlock).open();
                    }));
            options.open();
        });
    }

    static Dialog confirmation(Long userId, BlockService blocks, Runnable afterBlock) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Block this account?");
        dialog.add(new Paragraph("You will no longer be able to message each other or view each other's profile and reviews. Listings will remain visible."));
        Paragraph error = new Paragraph();
        error.getElement().setAttribute("role", "alert");
        dialog.add(error);
        Button confirm = new Button("Confirm block");
        confirm.setDisableOnClick(true);
        confirm.addClickListener(event -> {
            try {
                var result = blocks.blockUser(userId);
                dialog.close();
                Notification.show(result == BlockService.Result.BLOCKED
                        ? "The account has been blocked." : "This account is already blocked.",
                        6000, Notification.Position.MIDDLE);
                afterBlock.run();
            } catch (IllegalArgumentException | AccessDeniedException ex) {
                error.setText(ex.getMessage());
            } catch (DataAccessException ex) {
                error.setText("The account could not be blocked. Select Try again.");
                confirm.setText("Try again");
            } finally { confirm.setEnabled(true); }
        });
        dialog.getFooter().add(new Button("Cancel", event -> dialog.close()), confirm);
        return dialog;
    }
}

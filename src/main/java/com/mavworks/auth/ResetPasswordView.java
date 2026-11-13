package com.mavworks.auth;

import com.mavworks.service.PasswordResetService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.dao.DataAccessException;

@Route(value = "reset-password", autoLayout = false)
@PageTitle("Choose a New Password")
@AnonymousAllowed
public class ResetPasswordView extends Main implements BeforeEnterObserver {
    private final PasswordResetService resets;
    private final PasswordField password = new PasswordField("New password");
    private final PasswordField confirmation = new PasswordField("Confirm new password");
    private final Paragraph feedback = new Paragraph();
    private final Button submit = new Button("Update password");
    private String token;

    public ResetPasswordView(PasswordResetService resets) {
        this.resets = resets;
        addClassName("auth-page");
        password.setWidthFull();
        confirmation.setWidthFull();
        password.setMinLength(8);
        password.setMaxLength(72);
        password.setHelperText("Use at least 8 characters.");
        password.setAutocomplete(com.vaadin.flow.component.textfield.Autocomplete.NEW_PASSWORD);
        confirmation.setAutocomplete(com.vaadin.flow.component.textfield.Autocomplete.NEW_PASSWORD);
        password.setRequiredIndicatorVisible(true);
        confirmation.setRequiredIndicatorVisible(true);
        feedback.getElement().setAttribute("role", "alert");
        submit.setDisableOnClick(true);
        submit.addClickListener(event -> updatePassword());
        var form = new VerticalLayout(new H1("Choose a new password"), password,
                confirmation, submit, feedback,
                new com.vaadin.flow.router.RouterLink("Back to login", LoginView.class));
        form.setMaxWidth("36rem");
        form.addClassName("auth-card");
        form.getStyle().set("margin", "auto");
        add(new com.mavworks.base.ui.UtaBrand(), form);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        token = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("token", java.util.List.of()).stream().findFirst().orElse(null);
        if (token == null || token.isBlank()) {
            feedback.setText("This password reset link is invalid or has expired.");
            submit.setEnabled(false);
        }
    }

    private void updatePassword() {
        try {
            resets.reset(token, password.getValue(), confirmation.getValue());
            password.clear();
            confirmation.clear();
            getUI().ifPresent(ui -> ui.navigate("login", QueryParameters.simple(
                    java.util.Map.of("reset", "success"))));
        } catch (IllegalArgumentException ex) {
            feedback.setText(ex.getMessage());
        } catch (DataAccessException ex) {
            feedback.setText("Your password could not be updated. Please try again.");
        } finally {
            submit.setEnabled(token != null && !token.isBlank());
        }
    }

    @Override
    protected void onDetach(com.vaadin.flow.component.DetachEvent event) {
        password.clear();
        confirmation.clear();
        token = null;
        super.onDetach(event);
    }
}

package com.mavworks.auth;

import com.mavworks.service.AccountAccessService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "reset-password", autoLayout = false)
@PageTitle("Reset password")
@AnonymousAllowed
public class ResetPasswordView extends Main implements BeforeEnterObserver {
    private String token;
    public ResetPasswordView(AccountAccessService access) {
        addClassName("auth-page");
        var password = new PasswordField("New password");
        var confirmation = new PasswordField("Confirm new password");
        for (var field : java.util.List.of(password, confirmation)) {
            field.setWidthFull(); field.setRequiredIndicatorVisible(true);
            field.setAutocomplete(com.vaadin.flow.component.textfield.Autocomplete.NEW_PASSWORD);
        }
        password.setHelperText("Use at least 8 characters.");
        var status = new Paragraph(); status.getElement().setAttribute("role", "alert");
        var save = new Button("Reset password"); save.setDisableOnClick(true);
        save.addClickListener(event -> {
            try {
                access.resetPassword(token, password.getValue(), confirmation.getValue());
                token = null; password.clear(); confirmation.clear();
                getUI().ifPresent(ui -> ui.navigate("login", QueryParameters.simple(java.util.Map.of("reset", "true"))));
            } catch (IllegalArgumentException ex) { status.setText(ex.getMessage()); }
            catch (org.springframework.dao.DataAccessException ex) { status.setText("Your password could not be reset. Please try again later."); }
            finally { save.setEnabled(true); }
        });
        var card = new VerticalLayout(new H1("Reset password"), password, confirmation, save, status,
                new RouterLink("Request a new recovery link", ForgotPasswordView.class), new RouterLink("Back to login", LoginView.class));
        card.addClassName("auth-card"); add(new com.mavworks.base.ui.UtaBrand(), card);
    }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        token = event.getLocation().getQueryParameters().getSingleParameter("token").orElse(null);
    }
}

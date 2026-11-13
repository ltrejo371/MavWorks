package com.mavworks.auth;

import com.mavworks.service.AccountAccessService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "forgot-password", autoLayout = false)
@PageTitle("Account recovery")
@AnonymousAllowed
public class ForgotPasswordView extends Main implements BeforeEnterObserver {
    private final ComboBox<String> action = new ComboBox<>("Recovery option");
    public ForgotPasswordView(AccountAccessService access) {
        addClassName("auth-page");
        var email = new EmailField("Account email"); email.setRequiredIndicatorVisible(true); email.setWidthFull();
        email.setAutocomplete(com.vaadin.flow.component.textfield.Autocomplete.EMAIL);
        action.setItems("Reset password", "Unlock account"); action.setValue("Reset password"); action.setWidthFull();
        var status = new Paragraph(); status.getElement().setAttribute("role", "status");
        var send = new Button("Send recovery link"); send.setDisableOnClick(true);
        send.addClickListener(event -> {
            try {
                if (email.isEmpty() || email.isInvalid()) throw new IllegalArgumentException("Enter a valid email address.");
                access.requestRecovery(email.getValue(), "Unlock account".equals(action.getValue())
                        ? AccountAccessService.UNLOCK : AccountAccessService.RESET);
                status.setText(AccountAccessService.REQUEST_MESSAGE);
            } catch (IllegalArgumentException ex) { status.setText(ex.getMessage()); }
            catch (org.springframework.dao.DataAccessException ex) { status.setText("Recovery is unavailable. Please try again later."); }
            finally { send.setEnabled(true); }
        });
        var card = new VerticalLayout(new H1("Account recovery"),
                new Paragraph("Enter the email associated with your MavWorks account."), email, action, send, status,
                new RouterLink("Back to login", LoginView.class));
        card.addClassName("auth-card"); add(new com.mavworks.base.ui.UtaBrand(), card);
    }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        action.setValue("unlock".equals(event.getLocation().getQueryParameters().getSingleParameter("mode").orElse(""))
                ? "Unlock account" : "Reset password");
    }
}

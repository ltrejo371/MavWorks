package com.mavworks.auth;

import com.mavworks.service.AccountAccessService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "unlock-account", autoLayout = false)
@PageTitle("Unlock account")
@AnonymousAllowed
public class UnlockAccountView extends Main implements BeforeEnterObserver {
    private String token;
    public UnlockAccountView(AccountAccessService access) {
        addClassName("auth-page");
        var status = new Paragraph(); status.getElement().setAttribute("role", "alert");
        var unlock = new Button("Unlock my account"); unlock.setDisableOnClick(true);
        // A deliberate click prevents email scanners from consuming the single-use link.
        unlock.addClickListener(event -> {
            try {
                access.unlock(token); token = null;
                getUI().ifPresent(ui -> ui.navigate("login", QueryParameters.simple(java.util.Map.of("unlocked", "true"))));
            } catch (IllegalArgumentException ex) { status.setText(ex.getMessage()); }
            catch (org.springframework.dao.DataAccessException ex) { status.setText("Your account could not be unlocked. Please try again later."); }
            finally { unlock.setEnabled(true); }
        });
        var card = new VerticalLayout(new H1("Unlock account"), new Paragraph("Confirm to reopen your MavWorks account."),
                unlock, status, new RouterLink("Request a new recovery link", ForgotPasswordView.class), new RouterLink("Back to login", LoginView.class));
        card.addClassName("auth-card"); add(new com.mavworks.base.ui.UtaBrand(), card);
    }
    @Override public void beforeEnter(BeforeEnterEvent event) {
        token = event.getLocation().getQueryParameters().getSingleParameter("token").orElse(null);
    }
}

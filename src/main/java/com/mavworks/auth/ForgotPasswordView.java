package com.mavworks.auth;

import com.mavworks.service.PasswordResetService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.dao.DataAccessException;

@Route(value = "forgot-password", autoLayout = false)
@PageTitle("Forgot Password")
@AnonymousAllowed
public class ForgotPasswordView extends Main {
    private final EmailField email = new EmailField("Email");
    private final Paragraph feedback = new Paragraph();
    private final Button submit = new Button("Send reset link");
    private final PasswordResetService resets;

    public ForgotPasswordView(PasswordResetService resets) {
        this.resets = resets;
        addClassName("auth-page");
        email.setWidthFull();
        email.setMaxLength(254);
        email.setRequiredIndicatorVisible(true);
        email.setAutocomplete(com.vaadin.flow.component.textfield.Autocomplete.EMAIL);
        feedback.getElement().setAttribute("role", "status");
        submit.setDisableOnClick(true);
        submit.addClickListener(event -> requestReset());
        var form = new VerticalLayout(new H1("Reset your password"),
                new Paragraph("Enter the email address associated with your account."),
                email, submit, feedback, new RouterLink("Back to login", LoginView.class));
        form.setMaxWidth("36rem");
        form.addClassName("auth-card");
        form.getStyle().set("margin", "auto");
        add(new com.mavworks.base.ui.UtaBrand(), form);
    }

    private void requestReset() {
        try {
            resets.request(email.getValue());
            feedback.setText("If an account exists for that email, a password reset link has been sent.");
        } catch (DataAccessException ex) {
            feedback.setText("We could not process the request right now. Please try again later.");
        } finally {
            submit.setEnabled(true);
        }
    }
}

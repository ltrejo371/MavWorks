package com.mavworks.auth;

import com.mavworks.service.AccountService;
import com.mavworks.security.RegistrationLogin;
import com.mavworks.service.TermsOfServiceService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import jakarta.servlet.ServletException;
import java.util.Locale;
import org.springframework.dao.DataAccessException;

@Route(value = "create-account", autoLayout = false)
@PageTitle("Create Account")
@AnonymousAllowed
public class AccountCreateView extends Main {
    private final TextField username = new TextField("Username");
    private final EmailField email = new EmailField("University email");
    private final PasswordField password = new PasswordField("Password");
    private final PasswordField confirmation = new PasswordField("Confirm password");
    private final Checkbox acceptance = new Checkbox("I have read and accept the terms shown above.");
    private final Paragraph feedback = new Paragraph();
    private final AccountService accounts;
    private final RegistrationLogin registrationLogin;
    private Long termsId;

    public AccountCreateView(AccountService accounts, TermsOfServiceService termsService, RegistrationLogin registrationLogin) {
        this.accounts = accounts;
        this.registrationLogin = registrationLogin;
        username.setHelperText("3–30 letters, numbers, dots, underscores or hyphens. Case-insensitive.");
        username.setMaxLength(30);
        email.setPlaceholder("you@mavs.uta.edu");
        email.setMaxLength(254);
        password.setHelperText("At least 8 characters; maximum 72 UTF-8 bytes.");
        username.setRequiredIndicatorVisible(true);
        email.setRequiredIndicatorVisible(true);
        password.setRequiredIndicatorVisible(true);
        confirmation.setRequiredIndicatorVisible(true);
        feedback.getElement().setAttribute("role", "alert");
        feedback.setVisible(false);
        Paragraph termsText = new Paragraph();
        termsText.getStyle().set("white-space", "pre-wrap");
        Button create = new Button("Create account", event -> register());
        create.addThemeVariants(ButtonVariant.PRIMARY);
        var currentTerms = termsService.getCurrentTerms();
        if (currentTerms.isPresent()) {
            var terms = currentTerms.get();
            termsId = terms.getId();
            termsText.setText("Terms (" + terms.getVersion() + ")\n" + terms.getContent());
        } else {
            termsText.setText("Registration is temporarily unavailable: no terms have been published.");
            create.setEnabled(false);
        }
        VerticalLayout form = new VerticalLayout(new H1("Create Account"), username, email,
                password, confirmation, termsText, acceptance, feedback, create,
                new RouterLink("Back to login", LoginView.class));
        form.setMaxWidth("36rem");
        form.getStyle().set("margin", "auto");
        add(form);
    }

    private void register() {
        feedback.setVisible(false);
        if (!acceptance.getValue()) {
            showError("Please read and accept the terms before creating an account.");
            return;
        }
        try {
            accounts.register(username.getValue(), email.getValue(), password.getValue(),
                    confirmation.getValue(), termsId);
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            return;
        } catch (DataAccessException ex) {
            showError("Your account could not be saved. The username or email may already be taken. Please try again.");
            return;
        }
        try {
            var request = VaadinServletRequest.getCurrent().getHttpServletRequest();
            registrationLogin.signIn(request, username.getValue().trim().toLowerCase(Locale.ROOT), password.getValue());
            password.clear();
            confirmation.clear();
            getUI().ifPresent(ui -> ui.getPage().setLocation(request.getContextPath() + "/"));
        } catch (ServletException ex) {
            password.clear();
            confirmation.clear();
            showError("Your account was created. Use Back to login to sign in.");
        }
    }

    private void showError(String message) {
        feedback.setText(message);
        feedback.setVisible(true);
    }
}

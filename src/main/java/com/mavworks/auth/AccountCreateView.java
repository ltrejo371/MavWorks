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
    private final Checkbox acceptance = new Checkbox("I am at least 18 years old and agree to the Terms of Service shown above.");
    private final Paragraph feedback = new Paragraph();
    private final AccountService accounts;
    private final RegistrationLogin registrationLogin;
    private Long termsId;
    private java.util.UUID verificationToken;
    private final TextField verificationCode = new TextField("Email verification code");
    private final Button sendCode = new Button("Send verification code", event -> sendVerification());
    private final Button verify = new Button("Verify email and create account", event -> register());
    private final Button edit = new Button("Edit registration details", event -> editDetails());

    public AccountCreateView(AccountService accounts, TermsOfServiceService termsService, RegistrationLogin registrationLogin) {
        this.accounts = accounts;
        this.registrationLogin = registrationLogin;
        addClassName("auth-page");
        username.setWidthFull(); email.setWidthFull(); password.setWidthFull(); confirmation.setWidthFull();
        username.setHelperText("3–30 letters, numbers, dots, underscores or hyphens. Case-insensitive.");
        username.setMaxLength(30);
        email.setPlaceholder("you@mavs.uta.edu");
        email.setMaxLength(254);
        password.setHelperText("At least 8 characters.");
        username.setRequiredIndicatorVisible(true);
        email.setRequiredIndicatorVisible(true);
        password.setRequiredIndicatorVisible(true);
        confirmation.setRequiredIndicatorVisible(true);
        feedback.getElement().setAttribute("role", "alert");
        feedback.setVisible(false);
        Paragraph termsText = new Paragraph();
        termsText.addClassName("registration-terms");
        termsText.getStyle().set("white-space", "pre-wrap");
        sendCode.addThemeVariants(ButtonVariant.PRIMARY);
        sendCode.setDisableOnClick(true);
        verify.addThemeVariants(ButtonVariant.PRIMARY);
        verify.setDisableOnClick(true);
        verify.setVisible(false); edit.setVisible(false);
        verificationCode.setVisible(false);
        verificationCode.setWidthFull();
        verificationCode.setMinLength(6); verificationCode.setMaxLength(6);
        verificationCode.setPattern("[0-9]{6}");
        verificationCode.setAllowedCharPattern("[0-9]");
        verificationCode.setHelperText("Enter the 6-digit code sent to your university email. It expires in 10 minutes.");
        verificationCode.getElement().setAttribute("inputmode", "numeric");
        verificationCode.getElement().setAttribute("autocomplete", "one-time-code");
        var currentTerms = termsService.getCurrentTerms();
        if (currentTerms.isPresent()) {
            var terms = currentTerms.get();
            termsId = terms.getId();
            termsText.setText("Version " + terms.getVersion() + "\n" + terms.getContent());
        } else {
            termsText.setText("Registration is temporarily unavailable: no terms have been published.");
            sendCode.setEnabled(false);
        }
        VerticalLayout form = new VerticalLayout(new H1("Create Account"), username, email,
                password, confirmation, termsText, new RouterLink("Read Terms of Service full page", TermsView.class), acceptance, feedback, sendCode, verificationCode, verify, edit,
                new RouterLink("Back to login", LoginView.class));
        form.setMaxWidth("36rem");
        form.addClassName("auth-card");
        form.getStyle().set("margin", "auto");
        add(new com.mavworks.base.ui.UtaBrand(), form);
    }

    private void sendVerification() {
        feedback.setVisible(false);
        if (!acceptance.getValue()) {
            showError("Please read and accept the terms before creating an account.");
            sendCode.setEnabled(termsId != null);
            return;
        }
        try {
            verificationToken = accounts.requestVerification(username.getValue(), email.getValue(), password.getValue(),
                    confirmation.getValue(), termsId);
            setDetailsEnabled(false);
            verificationCode.clear(); verificationCode.setVisible(true);
            verify.setVisible(true); edit.setVisible(true);
            sendCode.setText("Resend verification code");
            showError("A verification code was sent to " + email.getValue().trim() + ". Your account will be created after verification.");
            verificationCode.focus();
        } catch (IllegalArgumentException | com.mavworks.service.VerificationEmailSender.DeliveryException ex) {
            showError(ex.getMessage());
        } catch (DataAccessException ex) {
            showError("Unable to start email verification. Please try again later.");
        } finally { sendCode.setEnabled(termsId != null); }
    }

    private void setDetailsEnabled(boolean enabled) {
        username.setEnabled(enabled); email.setEnabled(enabled); password.setEnabled(enabled);
        confirmation.setEnabled(enabled); acceptance.setEnabled(enabled);
    }
    private void editDetails() {
        verificationToken = null; verificationCode.clear(); verificationCode.setVisible(false);
        verify.setVisible(false); edit.setVisible(false); setDetailsEnabled(true);
        sendCode.setText("Send verification code"); feedback.setVisible(false);
    }
    @Override protected void onDetach(com.vaadin.flow.component.DetachEvent event) {
        password.clear(); confirmation.clear(); verificationCode.clear(); verificationToken = null;
        super.onDetach(event);
    }

    private void register() {
        verify.setEnabled(true);
        feedback.setVisible(false);
        if (!acceptance.getValue()) {
            showError("Please read and accept the terms before creating an account.");
            return;
        }
        try {
            accounts.register(username.getValue(), email.getValue(), password.getValue(),
                    confirmation.getValue(), termsId, verificationToken, verificationCode.getValue().trim());
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            return;
        } catch (DataAccessException ex) {
            showError("Your account could not be saved. The username or email may already be taken. Please try again.");
            return;
        }
        verify.setEnabled(false); sendCode.setEnabled(false); edit.setEnabled(false); verificationCode.clear();
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

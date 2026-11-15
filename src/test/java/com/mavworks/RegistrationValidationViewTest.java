package com.mavworks;

import com.mavworks.auth.AccountCreateView;
import com.mavworks.model.TermsOfService;
import com.mavworks.security.RegistrationLogin;
import com.mavworks.service.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.textfield.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationValidationViewTest {
    AccountService accounts;
    AccountCreateView view;
    @BeforeEach void setup() {
        var serviceRouter = mock(com.vaadin.flow.server.VaadinService.class);
        var router = mock(com.vaadin.flow.router.Router.class);
        var registry = mock(com.vaadin.flow.server.RouteRegistry.class);
        when(serviceRouter.getRouter()).thenReturn(router);
        when(router.getRegistry()).thenReturn(registry);
        when(registry.getTargetUrl(any(), any(com.vaadin.flow.router.RouteParameters.class))).thenReturn(Optional.of("login"));
        com.vaadin.flow.server.VaadinService.setCurrent(serviceRouter);
        accounts = mock(AccountService.class);
        var terms = new TermsOfService();
        ReflectionTestUtils.setField(terms, "id", 1L);
        terms.setVersion("test"); terms.setContent("Test terms");
        var service = mock(TermsOfServiceService.class);
        when(service.getCurrentTerms()).thenReturn(Optional.of(terms));
        view = new AccountCreateView(accounts, service, mock(RegistrationLogin.class));
    }
    @AfterEach void cleanup() { com.vaadin.flow.server.VaadinService.setCurrent(null); }
    <T> T field(String name, Class<T> type) {
        return type.cast(ReflectionTestUtils.getField(view, name));
    }
    void validDetails() {
        field("username", TextField.class).setValue("student");
        field("email", EmailField.class).setValue("student@mavs.uta.edu");
        field("password", PasswordField.class).setValue("Password123");
        field("confirmation", PasswordField.class).setValue("Password123");
        field("acceptance", Checkbox.class).setValue(true);
    }
    @Test void emptySubmissionMarksAllFieldsWithoutSendingEmail() {
        field("sendCode", Button.class).click();
        assertTrue(field("username", TextField.class).isInvalid());
        assertTrue(field("email", EmailField.class).isInvalid());
        assertTrue(field("password", PasswordField.class).isInvalid());
        assertTrue(field("confirmation", PasswordField.class).isInvalid());
        assertTrue(field("acceptance", Checkbox.class).isInvalid());
        assertFalse(field("username", TextField.class).getErrorMessage().isBlank());
        verifyNoInteractions(accounts);
    }
    @Test void correctionsAllowResubmissionAndPreserveValues() {
        validDetails();
        field("email", EmailField.class).setValue("student@example.com");
        field("confirmation", PasswordField.class).setValue("different");
        field("sendCode", Button.class).click();
        assertTrue(field("email", EmailField.class).isInvalid());
        assertTrue(field("confirmation", PasswordField.class).isInvalid());
        assertEquals("Password123", field("password", PasswordField.class).getValue());
        verifyNoInteractions(accounts);
        validDetails();
        when(accounts.requestVerification(anyString(), anyString(), anyString(), anyString(), eq(1L)))
                .thenReturn(UUID.randomUUID());
        field("sendCode", Button.class).click();
        assertFalse(field("email", EmailField.class).isInvalid());
        assertTrue(field("verificationCode", TextField.class).isVisible());
        verify(accounts).requestVerification("student", "student@mavs.uta.edu", "Password123", "Password123", 1L);
    }
    @Test void duplicateUsernameAppearsOnUsernameField() {
        validDetails();
        when(accounts.requestVerification(anyString(), anyString(), anyString(), anyString(), eq(1L)))
                .thenThrow(new RegistrationValidation.FieldException(RegistrationValidation.Field.USERNAME, "That username is already taken."));
        field("sendCode", Button.class).click();
        assertTrue(field("username", TextField.class).isInvalid());
        assertEquals("That username is already taken.", field("username", TextField.class).getErrorMessage());
        assertFalse(field("email", EmailField.class).isInvalid());
    }
    @Test void invalidAndRejectedCodesAppearOnCodeField() {
        validDetails();
        var token = UUID.randomUUID();
        when(accounts.requestVerification(anyString(), anyString(), anyString(), anyString(), eq(1L))).thenReturn(token);
        field("sendCode", Button.class).click();
        field("verify", Button.class).click();
        assertTrue(field("verificationCode", TextField.class).isInvalid());
        verify(accounts, never()).register(anyString(), anyString(), anyString(), anyString(), anyLong(), any(), anyString());
        field("verificationCode", TextField.class).setValue("123456");
        doThrow(new AccountServiceImpl.VerificationRejectedException()).when(accounts)
                .register(anyString(), anyString(), anyString(), anyString(), eq(1L), eq(token), eq("123456"));
        field("verify", Button.class).click();
        assertTrue(field("verificationCode", TextField.class).isInvalid());
        assertTrue(field("verificationCode", TextField.class).getErrorMessage().contains("expired"));
    }
    @Test void unicodePasswordLimitUsesBytesAndNormalizationIsPreserved() {
        assertTrue(RegistrationValidation.errors(" Student ", " Student@MAVS.UTA.EDU ", "Password123", "Password123").isEmpty());
        String password = "\u00e9".repeat(37);
        assertTrue(RegistrationValidation.errors("student", "student@mavs.uta.edu", password, password)
                .containsKey(RegistrationValidation.Field.PASSWORD));
    }
}

package com.mavworks;

import com.mavworks.auth.*;
import com.mavworks.service.AccountAccessService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.router.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryViewsTest {
    @org.junit.jupiter.api.BeforeEach void routing() {
        var service = mock(com.vaadin.flow.server.VaadinService.class);
        var router = mock(Router.class);
        var registry = mock(com.vaadin.flow.server.RouteRegistry.class);
        when(service.getRouter()).thenReturn(router); when(router.getRegistry()).thenReturn(registry);
        when(registry.getTargetUrl(any(), any(RouteParameters.class))).thenReturn(java.util.Optional.of("login"));
        com.vaadin.flow.server.VaadinService.setCurrent(service);
    }
    @org.junit.jupiter.api.AfterEach void clearRouting() { com.vaadin.flow.server.VaadinService.setCurrent(null); }
    @Test void resendLinkSelectsUnlockAndBlankEmailIsRejected() {
        var access = mock(AccountAccessService.class);
        var view = new ForgotPasswordView(access);
        view.beforeEnter(event("forgot-password?mode=unlock"));
        var option = components(view).filter(ComboBox.class::isInstance).map(ComboBox.class::cast).findFirst().orElseThrow();
        assertEquals("Unlock account", option.getValue());
        var send = button(view, "Send recovery link"); send.click(); verifyNoInteractions(access);
        var email = components(view).filter(EmailField.class::isInstance).map(EmailField.class::cast).findFirst().orElseThrow();
        email.setValue("student@mavs.uta.edu"); send.click();
        verify(access).requestRecovery("student@mavs.uta.edu", AccountAccessService.UNLOCK);
        assertTrue(send.isEnabled());
    }
    @Test void unlockRequiresExplicitClickAndPassesEmailToken() {
        var access = mock(AccountAccessService.class);
        var view = new UnlockAccountView(access); view.beforeEnter(event("unlock-account?token=example"));
        verifyNoInteractions(access);
        button(view, "Unlock my account").click(); verify(access).unlock("example");
    }
    @Test void resetPassesNewPasswordAndConfirmationAndDisplaysValidationErrors() {
        var access = mock(AccountAccessService.class);
        doThrow(new IllegalArgumentException("Passwords do not match.")).when(access).resetPassword("example", "new-password", "different");
        var view = new ResetPasswordView(access); view.beforeEnter(event("reset-password?token=example"));
        var fields = components(view).filter(PasswordField.class::isInstance).map(PasswordField.class::cast).toList();
        fields.get(0).setValue("new-password"); fields.get(1).setValue("different");
        var save = button(view, "Reset password"); save.click();
        verify(access).resetPassword("example", "new-password", "different");
        assertTrue(components(view).anyMatch(c -> "Passwords do not match.".equals(c.getElement().getText())));
        assertTrue(save.isEnabled());
    }
    private BeforeEnterEvent event(String location) {
        var event = mock(BeforeEnterEvent.class); when(event.getLocation()).thenReturn(new Location(location)); return event;
    }
    private Button button(Component root, String text) {
        return components(root).filter(c -> c instanceof Button b && b.getText().equals(text)).map(Button.class::cast).findFirst().orElseThrow();
    }
    private Stream<Component> components(Component root) { return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::components)); }
}

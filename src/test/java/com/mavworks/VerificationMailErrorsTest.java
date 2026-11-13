package com.mavworks;

import com.mavworks.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.*;
import org.springframework.mail.javamail.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class VerificationMailErrorsTest {
    @Test @SuppressWarnings("unchecked") void missingConfigurationExplainsTheSetupProblem() {
        var provider = (ObjectProvider<JavaMailSender>) mock(ObjectProvider.class);
        var sender = new SmtpVerificationEmailSender(provider, "");
        var error = assertThrows(VerificationEmailSender.DeliveryException.class, () -> sender.send("test@example.test", "123456"));
        assertTrue(error.getMessage().contains("not configured"));
        var smtp = new JavaMailSenderImpl(); smtp.setHost(""); when(provider.getIfAvailable()).thenReturn(smtp);
        error = assertThrows(VerificationEmailSender.DeliveryException.class,
                () -> new SmtpVerificationEmailSender(provider, "sender@example.test").send("test@example.test", "123456"));
        assertTrue(error.getMessage().contains("not configured"));
    }
    @Test @SuppressWarnings("unchecked") void authenticationFailureIsDistinguishedWithoutLeakingProviderDetails() {
        var provider = (ObjectProvider<JavaMailSender>) mock(ObjectProvider.class);
        var smtp = mock(JavaMailSender.class); when(provider.getIfAvailable()).thenReturn(smtp);
        doThrow(new MailAuthenticationException("private provider detail")).when(smtp).send(any(SimpleMailMessage.class));
        var error = assertThrows(VerificationEmailSender.DeliveryException.class,
                () -> new SmtpVerificationEmailSender(provider, "sender@example.test").send("test@example.test", "123456"));
        assertTrue(error.getMessage().contains("could not sign in"));
        assertFalse(error.getMessage().contains("private provider detail"));
    }
}

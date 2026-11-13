package com.mavworks.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpVerificationEmailSender implements VerificationEmailSender {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SmtpVerificationEmailSender.class);
    private final ObjectProvider<JavaMailSender> mail;
    private final String from;
    public SmtpVerificationEmailSender(ObjectProvider<JavaMailSender> mail,
            @Value("${mavworks.mail.from:}") String from) {
        this.mail = mail; this.from = from;
    }
    @Override public void send(String email, String code) {
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null || from.isBlank()
                || (sender instanceof org.springframework.mail.javamail.JavaMailSenderImpl smtp
                    && (smtp.getHost() == null || smtp.getHost().isBlank()))) {
            log.warn("Registration email is not configured. Set SMTP_HOST and MAIL_FROM, or configure data/mail.properties.");
            throw new DeliveryException("Verification email is not configured yet. Please contact the MavWorks administrator.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(email);
        message.setSubject("Verify your MavWorks email");
        message.setText("Your MavWorks verification code is: " + code
                + "\n\nThis code expires in 10 minutes. Enter it on the Create Account page to finish registration."
                + "\nDo not share this code. If you did not request it, you can ignore this email.");
        try { sender.send(message); }
        catch (MailAuthenticationException ex) {
            log.warn("Registration SMTP authentication failed. Check the mail account credentials and provider settings.");
            throw new DeliveryException("The email service could not sign in. Please contact the MavWorks administrator.");
        } catch (MailException ex) {
            // Do not log SMTP exception text: providers may include recipient addresses or message bodies.
            log.warn("Registration email delivery failed ({}). Check SMTP connectivity, TLS settings, and the authorized sender.", ex.getClass().getSimpleName());
            throw new DeliveryException();
        }
    }

}

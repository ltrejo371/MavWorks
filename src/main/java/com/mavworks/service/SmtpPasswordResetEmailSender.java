package com.mavworks.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpPasswordResetEmailSender implements PasswordResetEmailSender {
    private final ObjectProvider<JavaMailSender> mail;
    private final String from;

    public SmtpPasswordResetEmailSender(ObjectProvider<JavaMailSender> mail,
            @Value("${mavworks.mail.from:}") String from) {
        this.mail = mail;
        this.from = from;
    }

    @Override
    public void send(String email, String resetUrl) {
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null || from.isBlank()) throw new DeliveryException();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Reset your MavWorks password");
        message.setText("Use this link to reset your MavWorks password:\n\n" + resetUrl
                + "\n\nThis link expires in 30 minutes and can be used once. If you did not request a reset, ignore this email.");
        try {
            sender.send(message);
        } catch (MailException ex) {
            throw new DeliveryException();
        }
    }
}

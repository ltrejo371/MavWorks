package com.mavworks.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpVerificationEmailSender implements VerificationEmailSender {
    private final ObjectProvider<JavaMailSender> mail;
    private final String from;
    public SmtpVerificationEmailSender(ObjectProvider<JavaMailSender> mail,
            @Value("${mavworks.mail.from:}") String from) {
        this.mail = mail; this.from = from;
    }
    @Override public void send(String email, String code) {
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null || from.isBlank()) throw new DeliveryException();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(email);
        message.setSubject("Verify your MavWorks email");
        message.setText("Your MavWorks verification code is: " + code
                + "\n\nThis code expires in 10 minutes. Enter it on the Create Account page to finish registration."
                + "\nDo not share this code. If you did not request it, you can ignore this email.");
        try { sender.send(message); }
        catch (MailException ex) { throw new DeliveryException(); }
    }

}

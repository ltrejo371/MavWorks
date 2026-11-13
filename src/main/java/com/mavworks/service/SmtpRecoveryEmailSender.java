package com.mavworks.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpRecoveryEmailSender implements RecoveryEmailSender {
    private final ObjectProvider<JavaMailSender> mail;
    private final String from;
    public SmtpRecoveryEmailSender(ObjectProvider<JavaMailSender> mail, @Value("${mavworks.mail.from:}") String from) {
        this.mail = mail; this.from = from;
    }
    public void send(String email, String link, boolean unlock) {
        var sender = mail.getIfAvailable();
        if (sender == null || from.isBlank()) throw new DeliveryException();
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(email);
        message.setSubject(unlock ? "Unlock your MavWorks account" : "Reset your MavWorks password");
        message.setText((unlock ? "Your account was locked after three incorrect login attempts. Reopen it here:"
                : "Reset your MavWorks password here:") + "\n\n" + link
                + "\n\nThis link expires in 30 minutes and can be used once. Do not share it."
                + "\nIf you did not request this, your password has not changed. You can use Forgot Password to secure your account.");
        try { sender.send(message); } catch (MailException ex) { throw new DeliveryException(); }
    }
}

package com.mavworks.service;

public interface PasswordResetEmailSender {
    void send(String email, String resetUrl);

    class DeliveryException extends RuntimeException {
        public DeliveryException() {
            super("Unable to send the password reset email. Please try again later.");
        }
    }
}

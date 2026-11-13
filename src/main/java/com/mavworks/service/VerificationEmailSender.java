package com.mavworks.service;

public interface VerificationEmailSender {
    void send(String email, String code);
    class DeliveryException extends RuntimeException {
        public DeliveryException() { super("Unable to send the verification email. Please try again later."); }
        public DeliveryException(String message) { super(message); }
    }
}

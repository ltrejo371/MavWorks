package com.mavworks.service;

public interface RecoveryEmailSender {
    void send(String email, String link, boolean unlock);
    class DeliveryException extends RuntimeException {
        public DeliveryException() { super("Recovery email could not be sent."); }
    }
}

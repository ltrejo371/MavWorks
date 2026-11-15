package com.mavworks.service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

/** Registration rules shared by the form and the account service. */
public final class RegistrationValidation {
    private RegistrationValidation() {}
    public enum Field { USERNAME, EMAIL, PASSWORD, CONFIRMATION, TERMS }
    public static class FieldException extends IllegalArgumentException {
        private final Field field;
        public FieldException(Field field, String message) { super(message); this.field = field; }
        public Field field() { return field; }
    }
    public static Map<Field, String> errors(String username, String email, String password, String passwordConfirmation) {
        Map<Field, String> errors = new LinkedHashMap<>();
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!normalizedUsername.matches("[a-z0-9_.-]{3,30}")) {
            errors.put(Field.USERNAME, "Use 3–30 letters, numbers, dots, underscores or hyphens for your username.");
        }
        if (normalizedEmail.length() > 254
                || !normalizedEmail.matches("[a-z0-9_%+\\-]+(?:\\.[a-z0-9_%+\\-]+)*@mavs\\.uta\\.edu")) {
            errors.put(Field.EMAIL, "Enter a valid @mavs.uta.edu email address.");
        }
        if (password == null || password.length() < 8) {
            errors.put(Field.PASSWORD, "Use at least 8 characters for your password.");
        }
        if (password != null && password.getBytes(StandardCharsets.UTF_8).length > 72) {
            errors.put(Field.PASSWORD, "Your password is too long. Please use a shorter password.");
        }
        if (passwordConfirmation == null || passwordConfirmation.isEmpty() || !passwordConfirmation.equals(password)) {
            errors.put(Field.CONFIRMATION, "Passwords do not match.");
        }
        if (!errors.containsKey(Field.USERNAME) && TextModerationService.containsProhibitedLanguage(normalizedUsername)) {
            errors.put(Field.USERNAME, "Username contains prohibited language. Please choose another username.");
        }
        return errors;
    }
}

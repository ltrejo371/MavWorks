package com.mavworks.service;

import com.mavworks.model.PasswordResetToken;
import com.mavworks.repository.PasswordResetTokenRepository;
import com.mavworks.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwords;
    private final PasswordResetEmailSender mail;
    private final String publicUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
            PasswordEncoder passwords, PasswordResetEmailSender mail,
            @Value("${mavworks.public-url:http://localhost:8080}") String publicUrl) {
        this.users = users;
        this.tokens = tokens;
        this.passwords = passwords;
        this.mail = mail;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void request(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 254) return;
        users.findByEmailIgnoreCase(normalized).ifPresent(user -> {
            byte[] randomBytes = new byte[32];
            random.nextBytes(randomBytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
            String hash = hash(token);
            Instant now = Instant.now();
            tokens.deleteForUser(user.getId());
            tokens.saveAndFlush(new PasswordResetToken(hash, user, now.plus(TOKEN_LIFETIME)));
            try {
                mail.send(user.getEmail(), publicUrl + "/reset-password?token=" + token);
            } catch (PasswordResetEmailSender.DeliveryException ex) {
                logger.error("Unable to deliver a password reset email.");
            }
        });
    }

    @Transactional
    public void reset(String token, String password, String confirmation) {
        validatePassword(password, confirmation);
        if (token == null || !token.matches("[A-Za-z0-9_-]{40,50}")) {
            throw new IllegalArgumentException("Invalid or expired reset link.");
        }
        PasswordResetToken reset = tokens.findForToken(hash(token))
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset link."));
        Instant now = Instant.now();
        if (!reset.isUsable(now)) throw new IllegalArgumentException("Invalid or expired reset link.");
        reset.getUser().setHashedPassword(passwords.encode(password));
        reset.consume(now);
    }

    private static void validatePassword(String password, String confirmation) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Use at least 8 characters for your password.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Your password is too long. Please use a shorter password.");
        }
        if (!password.equals(confirmation)) throw new IllegalArgumentException("Passwords do not match.");
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }
}

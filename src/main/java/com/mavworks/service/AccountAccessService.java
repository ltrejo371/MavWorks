package com.mavworks.service;

import com.mavworks.model.User;
import com.mavworks.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAccessService {
    public static final String RESET = "reset", UNLOCK = "unlock";
    public static final String REQUEST_MESSAGE = "If the account is eligible, a recovery link will be sent to its email. Check your inbox and spam folder. You can request another link after one minute.";
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AccountAccessService.class);
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final UserDetailsService details;
    private final RecoveryEmailSender mail;
    private final String baseUrl;
    private final String dummyHash;
    private final SecureRandom random = new SecureRandom();

    public AccountAccessService(UserRepository users, PasswordEncoder passwords, UserDetailsService details,
            RecoveryEmailSender mail, @Value("${mavworks.public-base-url:http://localhost:8080}") String baseUrl) {
        this.users = users; this.passwords = passwords; this.details = details; this.mail = mail;
        var uri = java.net.URI.create(baseUrl);
        if (uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null
                || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && "localhost".equals(uri.getHost())))) {
            throw new IllegalArgumentException("Configure mavworks.public-base-url as an HTTPS URL (HTTP is allowed for localhost).");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.dummyHash = passwords.encode("unused-" + java.util.UUID.randomUUID());
    }

    // Hold the account row lock through password verification so simultaneous attempts cannot bypass the limit.
    @Transactional(noRollbackFor = AuthenticationException.class)
    public UserDetails authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty())
            throw new BadCredentialsException("Username and password are required.");
        var user = users.findForBlock(username.trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (user == null) {
            passwords.matches(password, dummyHash);
            throw new BadCredentialsException("Invalid credentials");
        }
        if (user.isBanned()) throw new DisabledException("Account unavailable");
        if (user.isLoginLocked()) throw new LockedException("Account locked");
        if (!passwords.matches(password, user.getHashedPassword())) {
            user.failedLogin();
            if (user.isLoginLocked()) {
                issue(user, UNLOCK);
                throw new LockedException("Account locked");
            }
            throw new BadCredentialsException("Invalid credentials");
        }
        user.successfulLogin();
        return details.loadUserByUsername(user.getUsername());
    }

    @Transactional
    public void requestRecovery(String email, String purpose) {
        if (!RESET.equals(purpose) && !UNLOCK.equals(purpose)) throw new IllegalArgumentException("Choose a recovery option.");
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 254 || !normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalArgumentException("Enter a valid email address.");
        users.findForRecoveryEmail(normalized).filter(u -> !u.isBanned())
                .filter(u -> RESET.equals(purpose) || u.isLoginLocked())
                .filter(u -> u.canSendRecovery(Instant.now())).ifPresent(u -> issue(u, purpose));
    }

    private void issue(User user, String purpose) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        user.issueRecovery(hash(token), purpose, Instant.now());
        try { mail.send(user.getEmail(), baseUrl + (UNLOCK.equals(purpose) ? "/unlock-account" : "/reset-password")
                    + "?token=" + token, UNLOCK.equals(purpose)); }
        catch (RecoveryEmailSender.DeliveryException ex) {
            // Delivery failure must never undo the failed attempt or account lock. Resend remains available.
            log.warn("Recovery email delivery failed; check SMTP configuration and availability.");
        }
    }

    @Transactional
    public void resetPassword(String token, String password, String confirmation) {
        if (password == null || password.length() < 8)
            throw new IllegalArgumentException("Use at least 8 characters for your password.");
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Your password is too long. Please use a shorter password.");
        if (!password.equals(confirmation)) throw new IllegalArgumentException("Passwords do not match.");
        var user = recoveryUser(token, RESET);
        user.setHashedPassword(passwords.encode(password));
        user.recoverAccess();
    }

    @Transactional
    public void unlock(String token) { recoveryUser(token, UNLOCK).recoverAccess(); }

    private User recoveryUser(String token, String purpose) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalidLink();
        return users.findForRecoveryToken(hash(token)).filter(u -> !u.isBanned())
                .filter(u -> u.acceptsRecovery(purpose, Instant.now())).orElseThrow(AccountAccessService::invalidLink);
    }
    private static IllegalArgumentException invalidLink() {
        return new IllegalArgumentException("This link is invalid, expired, or already used. Request a new recovery email.");
    }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}

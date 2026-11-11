package com.mavworks.service;

import com.mavworks.model.TOSAgreement;
import com.mavworks.model.EmailVerification;
import com.mavworks.model.User;
import com.mavworks.repository.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService {
    private final UserRepository users;
    private final TermsOfServiceRepository terms;
    private final TOSAgreementRepository agreements;
    private final PasswordEncoder passwords;
    private final EmailVerificationRepository verifications;
    private final VerificationEmailSender mail;
    private final java.security.SecureRandom random = new java.security.SecureRandom();
    public static class VerificationRejectedException extends IllegalArgumentException {
        public VerificationRejectedException() { super("Invalid or expired verification code. Request a new code if needed."); }
    }
    private record Validated(String username, String email, com.mavworks.model.TermsOfService terms) {}

    public AccountServiceImpl(UserRepository users, TermsOfServiceRepository terms,
            TOSAgreementRepository agreements, PasswordEncoder passwords, EmailVerificationRepository verifications, VerificationEmailSender mail) {
        this.users = users;
        this.terms = terms;
        this.agreements = agreements;
        this.passwords = passwords;
        this.verifications = verifications; this.mail = mail;
    }

    @Override
    @Transactional
    public java.util.UUID requestVerification(String username, String email, String password,
            String passwordConfirmation, Long acceptedTermsId) {
        Validated data = validate(username, email, password, passwordConfirmation, acceptedTermsId);
        EmailVerification verification = verifications.findForEmail(data.email())
                .orElseGet(() -> new EmailVerification(data.email()));
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        verification.issue(passwords.encode(code), Instant.now());
        verifications.saveAndFlush(verification);
        // Sending failures roll back the pending proof. No user or agreement exists yet.
        mail.send(data.email(), code);
        return verification.getToken();
    }

    @Override
    @Transactional(noRollbackFor = VerificationRejectedException.class)
    public void register(String username, String email, String password,
            String passwordConfirmation, Long acceptedTermsId, java.util.UUID verificationToken, String code) {
        Validated data = validate(username, email, password, passwordConfirmation, acceptedTermsId);
        if (verificationToken == null) throw new VerificationRejectedException();
        EmailVerification verification = verifications.findForToken(verificationToken)
                .orElseThrow(VerificationRejectedException::new);
        Instant now = Instant.now();
        if (!verification.getEmail().equals(data.email()) || !verification.canVerify(now))
            throw new VerificationRejectedException();
        if (code == null || !code.matches("[0-9]{6}") || !passwords.matches(code, verification.getCodeHash())) {
            verification.recordFailure();
            // This exception commits the failed attempt, so retries cannot reset the limit.
            throw new VerificationRejectedException();
        }
        User user = new User();
        user.setUsername(data.username());
        user.setEmail(data.email());
        user.setHashedPassword(passwords.encode(password));
        user.getProfile().setName(data.username());
        users.saveAndFlush(user);
        TOSAgreement agreement = new TOSAgreement();
        agreement.setUser(user);
        agreement.setTerms(data.terms());
        agreements.saveAndFlush(agreement);
        verification.consume(now);
    }

    private Validated validate(String username, String email, String password,
            String passwordConfirmation, Long acceptedTermsId) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!normalizedUsername.matches("[a-z0-9_.-]{3,30}")) {
            throw new IllegalArgumentException("Use 3–30 letters, numbers, dots, underscores or hyphens for your username.");
        }
        if (normalizedEmail.length() > 254
                || !normalizedEmail.matches("[a-z0-9_%+\\-]+(?:\\.[a-z0-9_%+\\-]+)*@mavs\\.uta\\.edu")) {
            throw new IllegalArgumentException("Enter a valid @mavs.uta.edu email address.");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Use at least 8 characters for your password.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Your password is too long. Please use a shorter password.");
        }
        if (!password.equals(passwordConfirmation)) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        var currentTerms = terms.findFirstByEffectiveAtLessThanEqualOrderByEffectiveAtDesc(Instant.now())
                .orElseThrow(() -> new IllegalArgumentException("Registration is unavailable until terms are published."));
        if (acceptedTermsId == null || !currentTerms.getId().equals(acceptedTermsId)) {
            throw new IllegalArgumentException("Read and accept the current terms. Refresh the page if they have changed.");
        }
        if (users.existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException("That username is already taken.");
        }
        if (users.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("An account already uses that email address.");
        }
        return new Validated(normalizedUsername, normalizedEmail, currentTerms);
    }
}
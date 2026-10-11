package com.mavworks.service;

import com.mavworks.model.TOSAgreement;
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

    public AccountServiceImpl(UserRepository users, TermsOfServiceRepository terms,
            TOSAgreementRepository agreements, PasswordEncoder passwords) {
        this.users = users;
        this.terms = terms;
        this.agreements = agreements;
        this.passwords = passwords;
    }

    @Override
    @Transactional
    public void register(String username, String email, String password,
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
        User user = new User();
        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setHashedPassword(passwords.encode(password));
        user.getProfile().setName(normalizedUsername);
        users.saveAndFlush(user);

        TOSAgreement agreement = new TOSAgreement();
        agreement.setUser(user);
        agreement.setTerms(currentTerms);
        agreements.saveAndFlush(agreement);
    }
}

package com.mavworks.service;


/** Registers an account and its terms acceptance atomically. */
public interface AccountService {
    // Validate the university email, unique username/email, matching passwords
    // and explicit TOS acceptance. Hash passwords; save user and agreement atomically.
    void register(String username, String email, String password,
            String passwordConfirmation, Long acceptedTermsId);
}


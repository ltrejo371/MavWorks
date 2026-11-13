package com.mavworks.service;

import java.util.UUID;

/** Creates accounts only after proof of ownership of the university email. */
public interface AccountService {
    UUID requestVerification(String username, String email, String password,
            String passwordConfirmation, Long acceptedTermsId);
    void register(String username, String email, String password,
            String passwordConfirmation, Long acceptedTermsId, UUID verificationToken, String code);
}

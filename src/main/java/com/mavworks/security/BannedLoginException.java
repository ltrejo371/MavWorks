package com.mavworks.security;

/** Carries identity only after the submitted password has been verified. */
public class BannedLoginException extends org.springframework.security.authentication.DisabledException {
    private final Long accountId;
    public BannedLoginException(Long accountId) { super("Account banned"); this.accountId = accountId; }
    public Long accountId() { return accountId; }
}

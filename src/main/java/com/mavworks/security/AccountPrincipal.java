package com.mavworks.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

/** Permanent identity survives username changes in every active session. */
public class AccountPrincipal extends org.springframework.security.core.userdetails.User {
    private final Long accountId;
    public AccountPrincipal(Long id, String username, String password, boolean enabled,
            Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, true, authorities);
        accountId = id;
    }
    public Long accountId() { return accountId; }
}

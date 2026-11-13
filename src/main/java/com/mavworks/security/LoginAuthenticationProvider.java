package com.mavworks.security;

import com.mavworks.service.AccountAccessService;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class LoginAuthenticationProvider implements AuthenticationProvider {
    private final AccountAccessService access;
    public LoginAuthenticationProvider(AccountAccessService access) { this.access = access; }
    @Override public Authentication authenticate(Authentication authentication) {
        var principal = access.authenticate(authentication.getName(),
                authentication.getCredentials() == null ? null : authentication.getCredentials().toString());
        var result = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        result.setDetails(authentication.getDetails());
        return result;
    }
    @Override public boolean supports(Class<?> type) { return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type); }
}

package com.mavworks.security;

import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Rechecks account status so an existing session cannot continue acting after a ban. */
@Component
public class CurrentUser {
    private final com.mavworks.repository.UserRepository users;

    public CurrentUser(com.mavworks.repository.UserRepository users) { this.users = users; }
    public Optional<String> username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return (authentication.getPrincipal() instanceof AccountPrincipal principal
                ? users.findById(principal.accountId()) : users.findByUsername(authentication.getName()))
                .filter(user -> !user.isBanned())
                .map(user -> user.getUsername());
    }
}

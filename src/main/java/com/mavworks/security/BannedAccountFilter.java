package com.mavworks.security;

import com.mavworks.repository.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Invalidates sessions after bans, account locks, or password recovery, including polling requests. */
public class BannedAccountFilter extends OncePerRequestFilter {
    private final UserRepository users;
    public BannedAccountFilter(UserRepository users) { this.users = users; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            var account = auth.getPrincipal() instanceof AccountPrincipal principal
                    ? users.findById(principal.accountId()) : users.findByUsername(auth.getName());
            boolean banned = account.map(u -> u.isBanned()).orElse(true);
            boolean invalid = account.map(u -> u.isLoginLocked()
                    || (auth.getPrincipal() instanceof AccountPrincipal principal
                        && principal.securityVersion() != u.getSecurityVersion())).orElse(true);
            if (banned || invalid) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                response.sendRedirect(request.getContextPath() + (banned ? "/login?banned" : "/login?session-ended"));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}

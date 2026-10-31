package com.mavworks.security;

import com.mavworks.repository.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Invalidates existing sessions on their next request, including Vaadin polling requests. */
public class BannedAccountFilter extends OncePerRequestFilter {
    private final UserRepository users;
    public BannedAccountFilter(UserRepository users) { this.users = users; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
                && (auth.getPrincipal() instanceof AccountPrincipal principal
                    ? users.findById(principal.accountId()) : users.findByUsername(auth.getName()))
                    .map(u -> u.isBanned()).orElse(true)) {
            SecurityContextHolder.clearContext();
            var session = request.getSession(false);
            if (session != null) session.invalidate();
            response.sendRedirect(request.getContextPath() + "/login?banned");
            return;
        }
        chain.doFilter(request, response);
    }
}

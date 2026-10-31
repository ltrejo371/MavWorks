package com.mavworks.security;

import com.mavworks.model.User;
import com.mavworks.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAccessTest {
    @Test void bannedUsersCannotLoginAndExistingSessionsAreInvalidated() throws Exception {
        var users = mock(UserRepository.class);
        var user = new User();
        ReflectionTestUtils.setField(user, "id", 43L);
        user.setUsername("banned"); user.setHashedPassword("test-only"); user.ban();
        when(users.findByUsername("banned")).thenReturn(Optional.of(user));
        assertFalse(new SecurityConfig().userDetailsService(users, "").loadUserByUsername("banned").isEnabled());
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        var session = new org.springframework.mock.web.MockHttpSession();
        request.setSession(session);
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("banned", null, java.util.List.of()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try {
            new BannedAccountFilter(users).doFilter(request, response, chain);
            assertTrue(session.isInvalid());
            assertEquals("/login?banned", response.getRedirectedUrl());
            verifyNoInteractions(chain);
            assertNull(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    @Test void onlyConfiguredAccountIdsReceiveAdminAuthority() {
        var users = mock(UserRepository.class);
        var user = new User();
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setUsername("admin");
        user.setHashedPassword("test-only");
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));
        var config = new SecurityConfig();
        assertFalse(config.userDetailsService(users, "").loadUserByUsername("admin").getAuthorities()
                .stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        assertTrue(config.userDetailsService(users, "42, 99").loadUserByUsername("admin").getAuthorities()
                .stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        ReflectionTestUtils.setField(user, "id", 43L);
        assertFalse(config.userDetailsService(users, "42").loadUserByUsername("admin").getAuthorities()
                .stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }
}

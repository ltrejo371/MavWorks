package com.mavworks;

import com.mavworks.model.User;
import com.mavworks.repository.UserRepository;
import com.mavworks.service.*;
import com.mavworks.security.AccountPrincipal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"vaadin.launch-browser=false", "vaadin.devserver.enabled=false",
        "mavworks.seed-development-terms=false", "spring.datasource.url=jdbc:h2:mem:login-recovery;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "mavworks.public-base-url=https://market.example.edu/app"})
class LoginRecoveryTest {
    private static final String PASSWORD = "Original-password-123";
    @Autowired UserRepository users;
    @Autowired AccountAccessService access;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthenticationConfiguration authentication;
    @MockitoBean RecoveryEmailSender mail;
    private User user;
    @BeforeEach void setup() {
        user = new User(); user.setUsername("recovery-" + UUID.randomUUID());
        user.setEmail(user.getUsername() + "@mavs.uta.edu"); user.setHashedPassword(passwords.encode(PASSWORD));
        user = users.saveAndFlush(user);
    }
    private org.springframework.security.core.Authentication login(String password) throws Exception {
        return authentication.getAuthenticationManager().authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(user.getUsername().toUpperCase(Locale.ROOT), password));
    }
    @Test void banDetailsRequireCorrectPasswordAndNeverAuthenticate() throws Exception {
        user.ban(); users.saveAndFlush(user);
        assertThrows(BadCredentialsException.class, () -> login("wrong"));
        var failure = assertThrows(com.mavworks.security.BannedLoginException.class, () -> login(PASSWORD));
        assertEquals(user.getId(), failure.accountId());
    }
    private User saved() { return users.findById(user.getId()).orElseThrow(); }
    private String token(boolean unlock) {
        var link = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).send(eq(user.getEmail()), link.capture(), eq(unlock));
        assertTrue(link.getValue().startsWith("https://market.example.edu/app/"));
        return link.getValue().substring(link.getValue().indexOf("?token=") + 7);
    }
    @Test void thirdFailurePersistsLockAndEmailUnlockIsSingleUse() throws Exception {
        assertThrows(BadCredentialsException.class, () -> login("wrong"));
        assertThrows(BadCredentialsException.class, () -> login("wrong"));
        assertEquals(2, saved().getFailedLoginAttempts()); verifyNoInteractions(mail);
        assertThrows(LockedException.class, () -> login("wrong"));
        assertTrue(saved().isLoginLocked()); assertEquals(3, saved().getFailedLoginAttempts());
        assertThrows(LockedException.class, () -> login(PASSWORD));
        String token = token(true);
        assertNotEquals(token, ReflectionTestUtils.getField(saved(), "recoveryTokenHash"));
        assertThrows(IllegalArgumentException.class, () -> access.resetPassword(token, "Changed-password", "Changed-password"));
        access.unlock(token);
        assertFalse(saved().isLoginLocked()); assertEquals(0, saved().getFailedLoginAttempts());
        assertThrows(IllegalArgumentException.class, () -> access.unlock(token));
        assertTrue(login(PASSWORD).isAuthenticated());
        verify(mail, times(1)).send(eq(user.getEmail()), anyString(), eq(true));
    }
    @Test void successfulLoginResetsConsecutiveFailuresAndBlankFieldsDoNotCount() throws Exception {
        assertThrows(BadCredentialsException.class, () -> login(""));
        assertEquals(0, saved().getFailedLoginAttempts());
        assertThrows(BadCredentialsException.class, () -> login("wrong"));
        assertTrue(login(PASSWORD).isAuthenticated());
        assertEquals(0, saved().getFailedLoginAttempts());
        assertThrows(BadCredentialsException.class, () -> login("wrong"));
        assertFalse(saved().isLoginLocked()); assertEquals(1, saved().getFailedLoginAttempts());
    }
    @Test void resetRequiresMatchingPasswordsExpiresAndCannotBeReused() throws Exception {
        var oldPrincipal = (AccountPrincipal) login(PASSWORD).getPrincipal();
        access.requestRecovery(" " + user.getEmail().toUpperCase(Locale.ROOT) + " ", AccountAccessService.RESET);
        String token = token(false);
        assertThrows(IllegalArgumentException.class, () -> access.unlock(token));
        assertThrows(IllegalArgumentException.class, () -> access.resetPassword(token, "Changed-password", "Mismatch"));
        assertThrows(IllegalArgumentException.class, () -> access.resetPassword(token, "short", "short"));
        access.resetPassword(token, "Changed-password", "Changed-password");
        assertTrue(passwords.matches("Changed-password", saved().getHashedPassword()));
        assertNotEquals(oldPrincipal.securityVersion(), saved().getSecurityVersion());
        assertThrows(IllegalArgumentException.class, () -> access.resetPassword(token, PASSWORD, PASSWORD));
        assertThrows(BadCredentialsException.class, () -> login(PASSWORD));
        assertTrue(login("Changed-password").isAuthenticated());
    }
    @Test void expiredLinksAreRejectedAndResendingInvalidatesPreviousLink() {
        access.requestRecovery(user.getEmail(), AccountAccessService.RESET);
        String first = token(false);
        var persisted = saved(); ReflectionTestUtils.setField(persisted, "recoveryExpiresAt", Instant.now().minusSeconds(1));
        ReflectionTestUtils.setField(persisted, "recoverySentAt", Instant.now().minusSeconds(61)); users.saveAndFlush(persisted);
        assertThrows(IllegalArgumentException.class, () -> access.resetPassword(first, PASSWORD, PASSWORD));
        access.requestRecovery(user.getEmail(), AccountAccessService.RESET);
        String second = token(false); assertNotEquals(first, second);
        assertThrows(IllegalArgumentException.class, () -> access.resetPassword(first, PASSWORD, PASSWORD));
        access.resetPassword(second, PASSWORD, PASSWORD);
    }
    @Test void recoveryRequestsAreThrottledAndUnknownOrBannedAccountsReceiveNoMail() {
        access.requestRecovery("unknown@mavs.uta.edu", AccountAccessService.RESET);
        access.requestRecovery(user.getEmail(), AccountAccessService.UNLOCK);
        verifyNoInteractions(mail);
        access.requestRecovery(user.getEmail(), AccountAccessService.RESET);
        access.requestRecovery(user.getEmail(), AccountAccessService.RESET);
        verify(mail, times(1)).send(eq(user.getEmail()), anyString(), eq(false));
        var persisted = saved(); persisted.ban(); users.saveAndFlush(persisted);
        assertThrows(IllegalArgumentException.class, () -> access.resetPassword(token(false), PASSWORD, PASSWORD));
        access.requestRecovery(user.getEmail(), AccountAccessService.RESET);
        verify(mail, times(1)).send(eq(user.getEmail()), anyString(), eq(false));
    }
    @Test void mailFailureDoesNotRollBackLockout() throws Exception {
        doThrow(new RecoveryEmailSender.DeliveryException()).when(mail).send(anyString(), anyString(), anyBoolean());
        assertThrows(BadCredentialsException.class, () -> login("wrong"));
        assertThrows(BadCredentialsException.class, () -> login("wrong"));
        assertThrows(LockedException.class, () -> login("wrong"));
        assertTrue(saved().isLoginLocked());
        assertThrows(LockedException.class, () -> login(PASSWORD));
    }
    @Test void concurrentFailuresCannotBypassThreeAttemptLimit() throws Exception {
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(3)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var tasks = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 3; i++) tasks.add(executor.submit(() -> {
                start.await();
                assertThrows(org.springframework.security.core.AuthenticationException.class, () -> login("wrong"));
                return null;
            }));
            start.countDown();
            for (var task : tasks) task.get(20, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(3, saved().getFailedLoginAttempts()); assertTrue(saved().isLoginLocked());
        verify(mail, times(1)).send(eq(user.getEmail()), anyString(), eq(true));
    }

    @Autowired org.springframework.web.context.WebApplicationContext web;
    @Test void bannedLoginRedirectCarriesNoticesOnlyAfterPasswordVerification() throws Exception {
        user.ban(); users.saveAndFlush(user);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web)
                .addFilters(security, (jakarta.servlet.Filter) (request, response, chain) ->
                        ((jakarta.servlet.http.HttpServletResponse) response).setStatus(204)).build();
        var session = new org.springframework.mock.web.MockHttpSession();
        var page = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/login").session(session)).andReturn();
        var csrf = (org.springframework.security.web.csrf.CsrfToken) page.getRequest()
                .getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
        var verified = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/login").session(session)
                .param(csrf.getParameterName(), csrf.getToken()).param("username", user.getUsername()).param("password", PASSWORD)).andReturn();
        assertEquals("/login?banned", verified.getResponse().getRedirectedUrl());
        assertNotNull(session.getAttribute("moderationBanNotices"));
        assertNull(session.getAttribute("SPRING_SECURITY_CONTEXT"));
        var incorrect = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/login").session(session)
                .param(csrf.getParameterName(), csrf.getToken()).param("username", user.getUsername()).param("password", "wrong")).andReturn();
        assertEquals("/login?error", incorrect.getResponse().getRedirectedUrl());
        assertNull(session.getAttribute("moderationBanNotices"));
    }
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("springSecurityFilterChain") jakarta.servlet.Filter security;
    @Test void formLoginShowsRequiredInvalidAndLockedStates() throws Exception {
        jakarta.servlet.Filter terminal = (request, response, chain) -> ((jakarta.servlet.http.HttpServletResponse) response).setStatus(204);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).addFilters(security, terminal).build();
        for (String path : List.of("/forgot-password", "/reset-password", "/unlock-account")) {
            assertEquals(204, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)).andReturn().getResponse().getStatus());
        }
        var session = new org.springframework.mock.web.MockHttpSession();
        var page = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/login").session(session)).andReturn();
        var csrf = (org.springframework.security.web.csrf.CsrfToken) page.getRequest().getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
        for (int i = 0; i < 4; i++) {
            var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/login").session(session)
                    .param(csrf.getParameterName(), csrf.getToken()).param("username", user.getUsername())
                    .param("password", i == 0 ? "" : "wrong")).andReturn();
            assertEquals("/login?" + (i == 0 ? "required" : i == 3 ? "locked" : "error"), result.getResponse().getRedirectedUrl());
        }
        assertTrue(saved().isLoginLocked());
    }
    @Test void resetInvalidatesAuthenticatedSessionsAndAlsoUnlocksTheAccount() throws Exception {
        var authenticated = login(PASSWORD);
        assertThrows(BadCredentialsException.class, () -> login("wrong"));
        assertThrows(BadCredentialsException.class, () -> login("wrong"));
        assertThrows(LockedException.class, () -> login("wrong"));
        var persisted = saved(); ReflectionTestUtils.setField(persisted, "recoverySentAt", Instant.now().minusSeconds(61));
        users.saveAndFlush(persisted);
        access.requestRecovery(user.getEmail(), AccountAccessService.RESET);
        access.resetPassword(token(false), "New-password-123", "New-password-123");
        assertFalse(saved().isLoginLocked());
        var session = new org.springframework.mock.web.MockHttpSession();
        var request = new org.springframework.mock.web.MockHttpServletRequest(); request.setSession(session);
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticated);
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try {
            var chain = mock(jakarta.servlet.FilterChain.class);
            new com.mavworks.security.BannedAccountFilter(users).doFilter(request, response, chain);
            assertTrue(session.isInvalid()); assertEquals("/login?session-ended", response.getRedirectedUrl());
            verifyNoInteractions(chain);
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
        assertTrue(login("New-password-123").isAuthenticated());
    }
}

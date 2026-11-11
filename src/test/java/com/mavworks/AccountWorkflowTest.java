package com.mavworks;

import com.mavworks.model.TermsOfService;
import com.mavworks.repository.*;
import com.mavworks.service.AccountService;
import jakarta.servlet.Filter;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties = {
        "vaadin.launch-browser=false", "vaadin.devserver.enabled=false",
        "spring.jpa.hibernate.ddl-auto=update", "mavworks.seed-development-terms=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountWorkflowTest {
    private static final String DATABASE = "jdbc:h2:file:./target/account-test-" + UUID.randomUUID();
    private static final String PASSWORD = "Example-password-123";
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE);
    }
    @Autowired AccountService accounts;
    @Autowired EmailVerificationRepository verifications;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.mavworks.service.VerificationEmailSender verificationMail;

    private void register(String username, String email, String password, String confirmation, Long termsId) {
        var token = accounts.requestVerification(username, email, password, confirmation, termsId);
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(verificationMail, org.mockito.Mockito.atLeastOnce()).send(
                org.mockito.ArgumentMatchers.eq(email.trim().toLowerCase(java.util.Locale.ROOT)), code.capture());
        accounts.register(username, email, password, confirmation, termsId, token, code.getValue());
    }
    @Autowired com.mavworks.security.RegistrationLogin registrationLogin;
    @Autowired UserRepository users;
    @Autowired TermsOfServiceRepository terms;
    @Autowired TOSAgreementRepository agreements;
    @Autowired PasswordEncoder encoder;
    @Autowired AuthenticationConfiguration authentication;
    @Autowired WebApplicationContext webContext;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;

    @Test @Order(1)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void registrationPersistsHashProfileAndAcceptance() {
        TermsOfService version = new TermsOfService();
        version.setVersion("test-v1");
        version.setContent("Test terms");
        version.setEffectiveAt(Instant.now().minusSeconds(60));
        terms.saveAndFlush(version);
        register(" Student ", " Student@MAVS.UTA.EDU ", PASSWORD, PASSWORD, version.getId());
        var user = users.findByUsername("student").orElseThrow();
        assertEquals("student@mavs.uta.edu", user.getEmail());
        assertNotEquals(PASSWORD, user.getHashedPassword());
        assertTrue(encoder.matches(PASSWORD, user.getHashedPassword()));
        assertEquals("student", user.getProfile().getName());
        assertTrue(agreements.existsByUserIdAndTermsId(user.getId(), version.getId()));
    }

    @Test @Order(2)
    void accountAuthenticatesAfterApplicationContextRestart() throws Exception {
        var manager = authentication.getAuthenticationManager();
        var result = manager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("STUDENT", PASSWORD));
        assertTrue(result.isAuthenticated());
        assertEquals("student", result.getName());
        assertThrows(AuthenticationException.class, () -> manager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("student", "wrong-password")));
        assertThrows(AuthenticationException.class, () -> manager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("user", "user")));
    }

    @Test @Order(3)
    void rejectsInvalidRegistrationWithoutAddingRows() {
        Long version = terms.findFirstByEffectiveAtLessThanEqualOrderByEffectiveAtDesc(Instant.now())
                .orElseThrow().getId();
        assertThrows(IllegalArgumentException.class, () ->
                register("student", "other@mavs.uta.edu", PASSWORD, PASSWORD, version));
        assertThrows(IllegalArgumentException.class, () ->
                register("another", "STUDENT@mavs.uta.edu", PASSWORD, PASSWORD, version));
        assertThrows(IllegalArgumentException.class, () ->
                register("another", "other@example.com", PASSWORD, PASSWORD, version));
        assertThrows(IllegalArgumentException.class, () ->
                register("another", "other@mavs.uta.edu", PASSWORD, "mismatch", version));
        assertThrows(IllegalArgumentException.class, () ->
                register("another", "other@mavs.uta.edu", "short", "short", version));
        assertThrows(IllegalArgumentException.class, () ->
                register("another", "other@mavs.uta.edu", PASSWORD, PASSWORD, null));
        assertThrows(IllegalArgumentException.class, () ->
                register("another", "other@mavs.uta.edu", PASSWORD, PASSWORD, -1L));
        assertEquals(1, users.count());
        assertEquals(1, agreements.count());
    }

    @Test @Order(4)
    void formLoginStoresAuthenticatedSession() throws Exception {
        Filter terminal = (request, response, chain) -> ((jakarta.servlet.http.HttpServletResponse) response).setStatus(204);
        var mvc = MockMvcBuilders.webAppContextSetup(webContext).addFilters(security, terminal).build();
        var page = mvc.perform(get("/login")).andReturn();
        var csrf = (org.springframework.security.web.csrf.CsrfToken)
                page.getRequest().getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
        assertNotNull(csrf);
        var token = csrf.getToken();
        var session = (org.springframework.mock.web.MockHttpSession) page.getRequest().getSession();
        var login = mvc.perform(post("/login").session(session)
                .param(csrf.getParameterName(), token)
                .param("username", "student").param("password", PASSWORD)).andReturn();
        assertEquals(302, login.getResponse().getStatus());
        assertEquals("/", login.getResponse().getRedirectedUrl());
        var authenticatedSession = login.getRequest().getSession(false);
        assertNotNull(authenticatedSession);
        var context = (org.springframework.security.core.context.SecurityContext)
                authenticatedSession.getAttribute("SPRING_SECURITY_CONTEXT");
        assertNotNull(context);
        assertEquals("student", context.getAuthentication().getName());
    }

    @Test @Order(5)
    void registrationAutoLoginSavesSessionAndRotatesSessionId() throws Exception {
        Filter autoLogin = (request, response, chain) -> {
            var httpRequest = (jakarta.servlet.http.HttpServletRequest) request;
            registrationLogin.signIn(httpRequest, "student", PASSWORD);
            ((jakarta.servlet.http.HttpServletResponse) response).setStatus(204);
        };
        var mvc = MockMvcBuilders.webAppContextSetup(webContext)
                .addFilters(security, autoLogin).build();
        var session = new org.springframework.mock.web.MockHttpSession();
        String oldId = session.getId();
        var result = mvc.perform(get("/login").session(session)).andReturn();
        assertEquals(204, result.getResponse().getStatus());
        var updated = result.getRequest().getSession(false);
        assertNotNull(updated);
        assertNotEquals(oldId, updated.getId());
        var context = (org.springframework.security.core.context.SecurityContext)
                updated.getAttribute("SPRING_SECURITY_CONTEXT");
        assertNotNull(context);
        assertEquals("student", context.getAuthentication().getName());
    }

    @Test @Order(6)
    void logoutInvalidatesSessionAndRedirectsToLogin() throws Exception {
        Filter terminal = (request, response, chain) -> ((jakarta.servlet.http.HttpServletResponse) response).setStatus(204);
        var mvc = MockMvcBuilders.webAppContextSetup(webContext).addFilters(security, terminal).build();
        var session = new org.springframework.mock.web.MockHttpSession();
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication.getAuthenticationManager().authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("student", PASSWORD)));
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
        var page = mvc.perform(get("/login").session(session)).andReturn();
        var csrf = (org.springframework.security.web.csrf.CsrfToken) page.getRequest()
                .getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
        assertNotNull(csrf);
        var result = mvc.perform(post("/logout").session(session).param(csrf.getParameterName(), csrf.getToken())).andReturn();
        assertEquals(302, result.getResponse().getStatus());
        assertEquals("/login", result.getResponse().getRedirectedUrl());
        assertTrue(session.isInvalid());
        assertNull(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
    }
}

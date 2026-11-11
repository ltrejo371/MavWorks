package com.mavworks;

import com.mavworks.model.TermsOfService;
import com.mavworks.repository.*;
import com.mavworks.service.AccountService;
import jakarta.servlet.Filter;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "vaadin.launch-browser=false", "vaadin.devserver.enabled=false",
        "mavworks.seed-development-terms=false",
        "spring.datasource.url=jdbc:h2:mem:acceptance-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=mavworks",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/postgresql",
        "spring.flyway.default-schema=mavworks", "spring.flyway.schemas=mavworks",
        "spring.flyway.create-schemas=true"
})
class AccountAcceptanceTest {
    private static final String PASSWORD = "Test-password-2026";
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
    @Autowired UserRepository users;
    @Autowired TermsOfServiceRepository terms;
    @Autowired TOSAgreementRepository agreements;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext webContext;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    private Long termsId;

    @BeforeEach
    void resetIsolatedDatabase() {
        verifications.deleteAll();
        agreements.deleteAll();
        users.deleteAll();
        terms.deleteAll();
        TermsOfService version = new TermsOfService();
        version.setVersion("acceptance-v1");
        version.setContent("Terms for automated testing only");
        version.setEffectiveAt(Instant.now().minusSeconds(60));
        termsId = terms.saveAndFlush(version).getId();
    }

    @Test @DisplayName("TC-01: valid registration creates an account")
    void tc01ValidRegistration() {
        register("student", "student@mavs.uta.edu", PASSWORD, PASSWORD, termsId);
        assertEquals(1, users.count());
        assertTrue(users.findByUsername("student").isPresent());
        assertEquals(1, agreements.count());
    }

    @Test @DisplayName("TC-02: malformed and disallowed email addresses are rejected")
    void tc02InvalidEmail() {
        for (String email : new String[] {"not-an-email", "student@", "@mavs.uta.edu",
                "student@@mavs.uta.edu", "student..name@mavs.uta.edu", "student@example.com"}) {
            assertThrows(IllegalArgumentException.class, () ->
                    register("student", email, PASSWORD, PASSWORD, termsId), email);
            assertEquals(0, users.count());
            assertEquals(0, agreements.count());
        }
    }

    @Test @DisplayName("TC-03: missing required registration information is rejected")
    void tc03MissingRequiredInformation() {
        String[][] missing = {
                {"", "student@mavs.uta.edu", PASSWORD, PASSWORD},
                {null, "student@mavs.uta.edu", PASSWORD, PASSWORD},
                {"student", "", PASSWORD, PASSWORD},
                {"student", null, PASSWORD, PASSWORD},
                {"student", "student@mavs.uta.edu", "", ""},
                {"student", "student@mavs.uta.edu", null, null},
                {"student", "student@mavs.uta.edu", PASSWORD, ""},
                {"student", "student@mavs.uta.edu", PASSWORD, null}
        };
        for (String[] input : missing) {
            assertThrows(IllegalArgumentException.class, () ->
                    register(input[0], input[1], input[2], input[3], termsId));
            assertEquals(0, users.count());
            assertEquals(0, agreements.count());
        }
        assertThrows(IllegalArgumentException.class, () ->
                register("student", "student@mavs.uta.edu", PASSWORD, PASSWORD, null));
        assertEquals(0, users.count());
        assertEquals(0, agreements.count());
    }

    @Test @DisplayName("TC-04: duplicate email does not create a second account")
    void tc04ExistingEmail() {
        register("student", "student@mavs.uta.edu", PASSWORD, PASSWORD, termsId);
        assertThrows(IllegalArgumentException.class, () ->
                register("different", "STUDENT@mavs.uta.edu", PASSWORD, PASSWORD, termsId));
        assertEquals(1, users.count());
        assertEquals(1, agreements.count());
        assertTrue(users.findByUsername("different").isEmpty());
    }

    @Test @DisplayName("TC-05: valid username/password authenticates and opens Home")
    void tc05ValidLogin() throws Exception {
        register("student", "student@mavs.uta.edu", PASSWORD, PASSWORD, termsId);
        var result = login("student", PASSWORD);
        assertEquals(302, result.getResponse().getStatus());
        assertEquals("/", result.getResponse().getRedirectedUrl());
        var context = authenticationContext(result);
        assertNotNull(context);
        assertTrue(context.getAuthentication().isAuthenticated());
        assertEquals("student", context.getAuthentication().getName());
    }

    @Test @DisplayName("TC-06: incorrect password fails authentication")
    void tc06IncorrectPassword() throws Exception {
        register("student", "student@mavs.uta.edu", PASSWORD, PASSWORD, termsId);
        assertRejected(login("student", "incorrect-password"));
    }

    @Test @DisplayName("TC-07: unregistered email fails authentication (login uses usernames)")
    void tc07UnregisteredEmail() throws Exception {
        assertRejected(login("unregistered@mavs.uta.edu", PASSWORD));
        assertEquals(0, users.count());
    }

    @Test @DisplayName("TC-08: committed database record has correct user data and password hash")
    void tc08DatabaseRecord() {
        Instant before = Instant.now().minusSeconds(1);
        register(" Student ", " Student@MAVS.UTA.EDU ", PASSWORD, PASSWORD, termsId);
        // Read through JDBC after the registration transaction commits, bypassing JPA's entity cache.
        Map<String, Object> row = jdbc.queryForMap(
                "select id, username, email, hashed_password, name from mavworks.app_users where username = ?",
                "student");
        assertNotNull(row.get("id"));
        assertEquals("student", row.get("username"));
        assertEquals("student@mavs.uta.edu", row.get("email"));
        assertEquals("student", row.get("name"));
        String hash = (String) row.get("hashed_password");
        assertNotEquals(PASSWORD, hash);
        assertTrue(passwords.matches(PASSWORD, hash));
        assertEquals(1L, jdbc.queryForObject(
                "select count(*) from mavworks.tos_agreements where user_id = ? and terms_id = ?",
                Long.class, row.get("id"), termsId));
        Instant accepted = jdbc.queryForObject(
                "select accepted_at from mavworks.tos_agreements where user_id = ?",
                (rs, number) -> rs.getTimestamp(1).toInstant(), row.get("id"));
        assertNotNull(accepted);
        assertFalse(accepted.isBefore(before));
        assertFalse(accepted.isAfter(Instant.now().plusSeconds(1)));
    }

    private MvcResult login(String username, String password) throws Exception {
        // Exercise the real Spring Security filter chain, without forwarding to the Vaadin servlet.
        Filter terminal = (request, response, chain) ->
                ((jakarta.servlet.http.HttpServletResponse) response).setStatus(204);
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(webContext).addFilters(security, terminal).build();
        var page = mvc.perform(get("/login")).andReturn();
        var csrf = (CsrfToken) page.getRequest().getAttribute(CsrfToken.class.getName());
        assertNotNull(csrf);
        String token = csrf.getToken();
        var session = (MockHttpSession) page.getRequest().getSession();
        return mvc.perform(post("/login").session(session)
                .param(csrf.getParameterName(), token)
                .param("username", username).param("password", password)).andReturn();
    }

    private record Pending(java.util.UUID token, String code) {}
    private Pending requestCode() {
        var token = accounts.requestVerification("student", "student@mavs.uta.edu", PASSWORD, PASSWORD, termsId);
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(verificationMail, org.mockito.Mockito.atLeastOnce())
                .send(org.mockito.ArgumentMatchers.eq("student@mavs.uta.edu"), code.capture());
        return new Pending(token, code.getValue());
    }
    private void verifyCode(Pending pending, String code) {
        accounts.register("student", "student@mavs.uta.edu", PASSWORD, PASSWORD, termsId, pending.token(), code);
    }
    private void allowResend() {
        var pending = verifications.findById("student@mavs.uta.edu").orElseThrow();
        org.springframework.test.util.ReflectionTestUtils.setField(pending, "sentAt", Instant.now().minusSeconds(61));
        verifications.saveAndFlush(pending);
    }
    @Test void requestingCodeDoesNotCreateAccountAndVerificationCannotBeBypassed() {
        assertThrows(IllegalArgumentException.class, () -> accounts.register("student", "student@mavs.uta.edu",
                PASSWORD, PASSWORD, termsId, null, "123456"));
        var pending = requestCode();
        assertTrue(pending.code().matches("[0-9]{6}"));
        assertEquals(0, users.count()); assertEquals(0, agreements.count());
        String hash = verifications.findById("student@mavs.uta.edu").orElseThrow().getCodeHash();
        assertNotEquals(pending.code(), hash); assertTrue(passwords.matches(pending.code(), hash));
        assertThrows(IllegalArgumentException.class, () -> accounts.register("student", "other@mavs.uta.edu",
                PASSWORD, PASSWORD, termsId, pending.token(), pending.code()));
        verifyCode(pending, pending.code());
        assertEquals(1, users.count()); assertEquals(1, agreements.count());
        assertNotNull(verifications.findById("student@mavs.uta.edu").orElseThrow().getConsumedAt());
        agreements.deleteAll(); users.deleteAll();
        assertThrows(IllegalArgumentException.class, () -> verifyCode(pending, pending.code()));
    }
    @Test void fiveWrongCodesPersistAndLockOutCorrectCode() {
        var pending = requestCode();
        String wrong = pending.code().equals("000000") ? "000001" : "000000";
        for (int i = 1; i <= 5; i++) {
            assertThrows(IllegalArgumentException.class, () -> verifyCode(pending, wrong));
            assertEquals(i, verifications.findById("student@mavs.uta.edu").orElseThrow().getAttempts());
        }
        assertThrows(IllegalArgumentException.class, () -> verifyCode(pending, pending.code()));
        assertEquals(0, users.count());
    }
    @Test void expiredCodesAreRejectedAndResendInvalidatesOriginalToken() {
        var first = requestCode();
        var stored = verifications.findById("student@mavs.uta.edu").orElseThrow();
        org.springframework.test.util.ReflectionTestUtils.setField(stored, "expiresAt", Instant.now().minusSeconds(1));
        verifications.saveAndFlush(stored);
        assertThrows(IllegalArgumentException.class, () -> verifyCode(first, first.code()));
        assertThrows(IllegalArgumentException.class, this::requestCode);
        allowResend(); var second = requestCode();
        assertNotEquals(first.token(), second.token());
        assertThrows(IllegalArgumentException.class, () -> verifyCode(first, first.code()));
        verifyCode(second, second.code()); assertEquals(1, users.count());
    }
    @Test void emailDeliveryIsLimitedToFivePerHour() {
        requestCode();
        for (int i = 1; i < 5; i++) { allowResend(); requestCode(); }
        allowResend(); assertThrows(IllegalArgumentException.class, this::requestCode);
        org.mockito.Mockito.verify(verificationMail, org.mockito.Mockito.times(5)).send(
                org.mockito.ArgumentMatchers.eq("student@mavs.uta.edu"), org.mockito.ArgumentMatchers.anyString());
    }
    @Test void deliveryFailureCreatesNeitherAccountNorPendingProof() {
        org.mockito.Mockito.doThrow(new com.mavworks.service.VerificationEmailSender.DeliveryException())
                .when(verificationMail).send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        assertThrows(com.mavworks.service.VerificationEmailSender.DeliveryException.class, this::requestCode);
        assertEquals(0, verifications.count()); assertEquals(0, users.count()); assertEquals(0, agreements.count());
    }
    @Test void failedResendKeepsPreviouslyDeliveredCodeValid() {
        var pending = requestCode(); allowResend();
        org.mockito.Mockito.doThrow(new com.mavworks.service.VerificationEmailSender.DeliveryException())
                .when(verificationMail).send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        assertThrows(com.mavworks.service.VerificationEmailSender.DeliveryException.class, this::requestCode);
        verifyCode(pending, pending.code()); assertEquals(1, users.count());
    }
    @Test void concurrentVerificationCreatesOneAccountAndAgreement() throws Exception {
        var pending = requestCode();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> attempt = () -> {
                start.await();
                try { verifyCode(pending, pending.code()); return true; }
                catch (IllegalArgumentException ex) { return false; }
            };
            var first = executor.submit(attempt); var second = executor.submit(attempt); start.countDown();
            assertNotEquals(first.get(10, java.util.concurrent.TimeUnit.SECONDS), second.get(10, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1, users.count()); assertEquals(1, agreements.count());
    }

    private SecurityContext authenticationContext(MvcResult result) {
        var session = result.getRequest().getSession(false);
        return session == null ? null : (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
    }

    private void assertRejected(MvcResult result) {
        assertEquals(302, result.getResponse().getStatus());
        assertEquals("/login?error", result.getResponse().getRedirectedUrl());
        assertNull(authenticationContext(result));
    }
}

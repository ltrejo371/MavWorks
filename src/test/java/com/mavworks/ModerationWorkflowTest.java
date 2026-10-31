package com.mavworks;

import com.mavworks.model.*;
import com.mavworks.repository.*;
import com.mavworks.service.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "vaadin.launch-browser=false", "vaadin.devserver.enabled=false", "mavworks.seed-development-terms=false",
        "spring.datasource.url=jdbc:h2:mem:moderation-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.default_schema=mavworks",
        "spring.flyway.enabled=true", "spring.flyway.locations=classpath:db/migration/postgresql",
        "spring.flyway.default-schema=mavworks", "spring.flyway.schemas=mavworks", "spring.flyway.create-schemas=true"
})
class ModerationWorkflowTest {
    @Autowired ReportService service;
    @Autowired ReportRepository reports;
    @Autowired UserRepository users;
    @Autowired AccountDashboardService dashboard;
    @Autowired MessagingService messaging;
    User reporter, target, admin, secondAdmin;
    Long reportId;

    @BeforeEach void setup() {
        reports.deleteAll();
        users.deleteAll();
        reporter = account("reporter"); target = account("target");
        admin = account("admin"); secondAdmin = account("second-admin");
        login(reporter, "USER");
        reportId = service.reportUser(target.getId(), ReportCategory.HARASSMENT, "Private allegation");
        login(admin, "ADMIN");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void openingAndLeavingReportDoesNotChangePendingStatus() {
        assertEquals(ReportStatus.PENDING, service.details(reportId).report().status());
        assertNull(service.details(reportId).reviewedAt());
        assertEquals(1, service.pendingReports(0).getTotalElements());
        assertEquals(ReportStatus.PENDING, reports.findById(reportId).orElseThrow().getStatus());
    }

    @Test void validWarningIsArchivedAuditedAndDeliveredPrivatelyToDashboard() {
        service.validateReport(reportId, ReportStatus.VALID, ModerationAction.WARNING, "Stop sending threats.");
        var detail = service.details(reportId);
        assertEquals(ReportStatus.VALID, detail.report().status());
        assertEquals("admin", detail.reviewedBy());
        assertNotNull(detail.reviewedAt());
        assertEquals(ModerationAction.WARNING, detail.action());
        assertTrue(service.pendingReports(0).isEmpty());
        assertEquals(1, service.reports(ReportStatus.VALID, 0).getTotalElements());
        assertFalse(users.findById(target.getId()).orElseThrow().isBanned());
        login(target, "USER");
        assertEquals("Stop sending threats.", dashboard.load().warnings().getFirst().explanation());
        login(reporter, "USER");
        assertTrue(dashboard.load().warnings().isEmpty());
    }

    @Test void invalidReportArchivesWithoutWarningOrBan() {
        service.validateReport(reportId, ReportStatus.INVALID, null, "Insufficient evidence.");
        assertTrue(service.pendingReports(0).isEmpty());
        assertEquals(1, service.reports(ReportStatus.INVALID, 0).getTotalElements());
        assertNull(service.details(reportId).action());
        login(target, "USER");
        assertTrue(dashboard.load().warnings().isEmpty());
        assertFalse(users.findById(target.getId()).orElseThrow().isBanned());
    }

    @Test void banStopsExistingAuthenticatedAccountFromUsingServices() {
        service.validateReport(reportId, ReportStatus.VALID, ModerationAction.BAN, "Severe repeated threats.");
        assertTrue(users.findById(target.getId()).orElseThrow().isBanned());
        assertEquals(ModerationAction.BAN, service.details(reportId).action());
        login(target, "USER"); // Represents a session created before the account was banned.
        assertThrows(AccessDeniedException.class, () -> messaging.getMyConversations());
        assertThrows(AccessDeniedException.class, () -> dashboard.load());
        assertThrows(AccessDeniedException.class, () -> service.reportUser(reporter.getId(), ReportCategory.OTHER, ""));
    }

    @Test void invalidDecisionsDoNotPartiallySaveAndOrdinaryUsersCannotAdjudicate() {
        assertThrows(IllegalArgumentException.class, () -> service.validateReport(reportId, null, null, ""));
        assertThrows(IllegalArgumentException.class, () -> service.validateReport(reportId, ReportStatus.PENDING, null, ""));
        assertThrows(IllegalArgumentException.class, () -> service.validateReport(reportId, ReportStatus.VALID, null, "Reason"));
        assertThrows(IllegalArgumentException.class, () -> service.validateReport(reportId, ReportStatus.VALID, ModerationAction.WARNING, " "));
        assertThrows(IllegalArgumentException.class, () -> service.validateReport(reportId, ReportStatus.INVALID, ModerationAction.BAN, "Reason"));
        assertEquals(ReportStatus.PENDING, service.details(reportId).report().status());
        login(reporter, "USER");
        assertThrows(AccessDeniedException.class, () -> service.validateReport(reportId, ReportStatus.INVALID, null, ""));
        assertThrows(AccessDeniedException.class, () -> service.details(reportId));
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> service.validateReport(reportId, ReportStatus.INVALID, null, ""));
    }

    @Test void previousOffensesAreCountedAndResolvedReportsCannotBeChanged() {
        service.validateReport(reportId, ReportStatus.VALID, ModerationAction.WARNING, "First warning");
        login(reporter, "USER");
        Long next = service.reportUser(target.getId(), ReportCategory.SPAM, "Another incident");
        login(admin, "ADMIN");
        assertEquals(1, service.details(next).priorValidReports());
        assertThrows(IllegalStateException.class, () -> service.validateReport(reportId, ReportStatus.INVALID, null, "Override"));
        assertEquals(ReportStatus.VALID, service.details(reportId).report().status());
    }

    @Test void twoAdminsCannotOverwriteEachOthersDecision() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> decide(start, admin, ReportStatus.VALID, ModerationAction.WARNING));
            var second = executor.submit(() -> decide(start, secondAdmin, ReportStatus.INVALID, null));
            start.countDown();
            assertEquals(Set.of("saved", "conflict"), Set.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)));
        }
        assertNotEquals(ReportStatus.PENDING, service.details(reportId).report().status());
        assertNotNull(service.details(reportId).reviewedBy());
    }

    private String decide(CountDownLatch start, User actor, ReportStatus status, ModerationAction action) throws Exception {
        login(actor, "ADMIN");
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
            service.validateReport(reportId, status, action, "Admin explanation");
            return "saved";
        } catch (IllegalStateException ex) { return "conflict"; }
        finally { SecurityContextHolder.clearContext(); }
    }
    private User account(String name) {
        var user = new User(); user.setUsername(name); user.setEmail(name + "@mavs.uta.edu"); user.setHashedPassword("test-only");
        return users.saveAndFlush(user);
    }
    private void login(User user, String role) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user.getUsername(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
    }
}

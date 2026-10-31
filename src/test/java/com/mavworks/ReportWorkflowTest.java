package com.mavworks;

import com.mavworks.model.*;
import com.mavworks.repository.*;
import com.mavworks.service.ReportService;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "vaadin.launch-browser=false", "vaadin.devserver.enabled=false", "mavworks.seed-development-terms=false",
        "spring.datasource.url=jdbc:h2:mem:report-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.default_schema=mavworks",
        "spring.flyway.enabled=true", "spring.flyway.locations=classpath:db/migration/postgresql",
        "spring.flyway.default-schema=mavworks", "spring.flyway.schemas=mavworks", "spring.flyway.create-schemas=true"
})
@Transactional
class ReportWorkflowTest {
    @Autowired ReportService service;
    @Autowired UserRepository users;
    @Autowired ReportRepository reports;
    @Autowired UserBlockRepository blocks;
    @Autowired EntityManager entityManager;
    User reporter, target, admin;

    @BeforeEach void setup() {
        reporter = account("reporter");
        target = account("target");
        admin = account("admin");
        login(reporter, "USER");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void submissionIsSavedAsPendingAndDeliveredToAdministratorQueue() {
        Long id = service.reportUser(target.getId(), ReportCategory.SCAM_OR_FRAUD, "  Misleading payment request  ");
        entityManager.clear();
        Report saved = reports.findById(id).orElseThrow();
        assertEquals(reporter.getId(), saved.getReporter().getId());
        assertEquals(target.getId(), saved.getReportedUser().getId());
        assertEquals(ReportStatus.PENDING, saved.getStatus());
        assertEquals("Misleading payment request", saved.getReason());
        assertEquals(0, blocks.count());
        login(admin, "ADMIN");
        var queue = service.pendingReports(0);
        assertEquals(1, queue.getTotalElements());
        assertEquals(id, queue.getContent().getFirst().id());
        assertEquals("reporter", queue.getContent().getFirst().reporter());
        assertEquals("target", queue.getContent().getFirst().reportedUser());
    }

    @Test void categoryIsRequiredAndInvalidSubmissionsSaveNothing() {
        assertThrows(IllegalArgumentException.class, () -> service.reportUser(target.getId(), null, "Reason"));
        assertThrows(IllegalArgumentException.class, () -> service.reportUser(null, ReportCategory.OTHER, ""));
        assertThrows(IllegalArgumentException.class, () -> service.reportUser(reporter.getId(), ReportCategory.OTHER, ""));
        assertThrows(IllegalArgumentException.class, () -> service.reportUser(Long.MAX_VALUE, ReportCategory.OTHER, ""));
        assertThrows(IllegalArgumentException.class, () -> service.reportUser(target.getId(), ReportCategory.OTHER, "x".repeat(4001)));
        assertEquals(0, reports.count());
    }

    @Test void optionalReasonsAndSeparateIncidentsAreAllowedEvenWithAnExistingBlock() {
        blocks.saveAndFlush(reporter.blockUser(target));
        service.reportUser(target.getId(), ReportCategory.SPAM, null);
        service.reportUser(target.getId(), ReportCategory.HARASSMENT, "x".repeat(4000));
        assertEquals(2, reports.count());
        assertEquals(1, blocks.count());
        assertTrue(reports.findAll().stream().anyMatch(r -> r.getReason().isEmpty()));
    }

    @Test void ordinaryUsersReportedUserAndAnonymousCannotReadReports() {
        service.reportUser(target.getId(), ReportCategory.OTHER, "Private reason");
        assertThrows(AccessDeniedException.class, () -> service.pendingReports(0));
        login(target, "USER");
        assertThrows(AccessDeniedException.class, () -> service.pendingReports(0));
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> service.pendingReports(0));
        assertThrows(AccessDeniedException.class, () -> service.reportUser(target.getId(), ReportCategory.OTHER, ""));
        assertEquals(1, reports.count());
    }

    @Test void deletingAccountsRetainsReportEvidenceForAdministrators() {
        service.reportUser(target.getId(), ReportCategory.HARASSMENT, "Retained evidence");
        entityManager.createNativeQuery("delete from mavworks.app_users where id in (:reporter, :target)")
                .setParameter("reporter", reporter.getId()).setParameter("target", target.getId()).executeUpdate();
        entityManager.clear();
        login(admin, "ADMIN");
        var item = service.pendingReports(0).getContent().getFirst();
        assertEquals("Deleted account", item.reporter());
        assertEquals("Deleted account", item.reportedUser());
        assertEquals("Retained evidence", item.reason());
        assertEquals(ReportStatus.PENDING, item.status());
    }

    private User account(String name) {
        var user = new User();
        user.setUsername(name);
        user.setEmail(name + "@mavs.uta.edu");
        user.setHashedPassword("test-only");
        return users.saveAndFlush(user);
    }
    private void login(User user, String role) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user.getUsername(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
    }
}

package com.mavworks;

import com.mavworks.model.*;
import com.mavworks.repository.*;
import com.mavworks.service.*;
import com.mavworks.security.*;
import com.mavworks.profile.ProfilePictureController;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "vaadin.launch-browser=false", "vaadin.devserver.enabled=false", "mavworks.seed-development-terms=false",
        "spring.datasource.url=jdbc:h2:mem:account-edit-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "mavworks.images-directory=target/profile-test-images"
})
class AccountEditingTest {
    @Autowired AccountEditingService editing;
    @Autowired AccountDashboardService dashboard;
    @Autowired UserRepository users;
    @Autowired ReportRepository reports;
    @Autowired UserBlockRepository blocks;
    @Autowired ReportService reporting;
    @Autowired BlockService blocking;
    @Autowired ReviewService profiles;
    @Autowired LocalImageStorageService images;
    @Autowired ProfilePictureController pictures;
    @Autowired org.springframework.web.context.WebApplicationContext webContext;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("springSecurityFilterChain") jakarta.servlet.Filter security;
    User owner, other;

    @BeforeEach void setup() {
        blocks.deleteAll(); reports.deleteAll(); users.deleteAll();
        owner = account("owner"); other = account("other"); login(owner, "USER");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    User account(String name) {
        User user = new User(); user.setUsername(name); user.setEmail(java.util.UUID.randomUUID() + "@mavs.uta.edu");
        user.setHashedPassword("test"); return users.saveAndFlush(user);
    }
    void login(User user, String role) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        var principal = new AccountPrincipal(user.getId(), user.getUsername(), "test", true, authorities);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
    @Test void renamePersistsAndSessionKeepsOriginalIdentityEvenIfOldNameIsReused() {
        editing.save(" New.Name ", "Updated bio", null);
        assertEquals("new.name", dashboard.load().username());
        assertEquals("Updated bio", profiles.getProviderProfile(owner.getId()).orElseThrow().profile().bio());
        assertEquals("new.name", profiles.getProviderProfile(owner.getId()).orElseThrow().profile().username());
        account("owner");
        editing.save("new.name", "Still original account", null);
        assertEquals(owner.getId(), dashboard.load().userId());
        assertEquals("Still original account", users.findById(owner.getId()).orElseThrow().getProfile().getBio());
        assertNull(users.findByUsername("owner").orElseThrow().getProfile());
        assertNull(users.findById(other.getId()).orElseThrow().getProfile());
    }
    @Test void invalidFieldsAndAnonymousUpdatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> editing.save(" OTHER ", "bad", null));
        assertThrows(IllegalArgumentException.class, () -> editing.save("?", "bad", null));
        assertThrows(IllegalArgumentException.class, () -> editing.save("owner", "x".repeat(2001), null));
        assertEquals("owner", dashboard.load().username());
        assertNull(dashboard.load().bio());
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> editing.save("changed", "", null));
        assertThrows(AccessDeniedException.class, () -> dashboard.load());
    }
    @Test void dashboardShowsOnlyOwnReportsWithLatestStatusAndOutgoingBlocks() {
        Long id = reporting.reportUser(other.getId(), ReportCategory.SPAM, "reason");
        blocking.blockUser(other.getId());
        assertEquals(ReportStatus.PENDING, dashboard.load().submittedReports().getFirst().status());
        assertEquals(List.of("other"), dashboard.load().blockedAccounts());
        login(other, "USER");
        assertTrue(dashboard.load().submittedReports().isEmpty());
        assertTrue(dashboard.load().blockedAccounts().isEmpty());
        User admin = account("admin"); login(admin, "ADMIN");
        reporting.validateReport(id, ReportStatus.INVALID, null, "No violation");
        login(owner, "USER");
        assertEquals(ReportStatus.INVALID, dashboard.load().submittedReports().getFirst().status());
    }
    @Test void pictureIsValidatedOwnedPersistedAndHiddenAfterBlock() throws Exception {
        var buffer = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", buffer);
        String reference = images.store(new java.io.ByteArrayInputStream(buffer.toByteArray()), "picture.png", "image/png");
        try {
            editing.save("owner", "Bio", reference);
            assertTrue(dashboard.load().hasPicture());
            assertArrayEquals(buffer.toByteArray(), pictures.picture(owner.getId()).getBody());
            login(other, "USER");
            assertThrows(IllegalArgumentException.class, () -> editing.save("other", "", reference));
            assertEquals(200, pictures.picture(owner.getId()).getStatusCode().value());
            blocking.blockUser(owner.getId());
            assertEquals(404, pictures.picture(owner.getId()).getStatusCode().value());
            login(owner, "USER");
            editing.save("owner", "New bio", null);
            assertTrue(dashboard.load().hasPicture());
        } finally { login(owner, "USER"); images.delete(reference); }
        assertThrows(IllegalArgumentException.class, () -> LocalImageStorageService.validate("not an image".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> LocalImageStorageService.validate(new byte[LocalImageStorageService.MAX_BYTES + 1]));
    }

    @Test void pictureLoadsThroughAuthenticatedHttpAndBlocksOtherViewers() throws Exception {
        var buffer = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", buffer);
        String reference = images.store(new java.io.ByteArrayInputStream(buffer.toByteArray()), "picture.png", "image/png");
        try {
            editing.save("owner", "", reference);
            var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext).addFilters(security).build();
            var session = new org.springframework.mock.web.MockHttpSession();
            session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            SecurityContextHolder.clearContext();
            var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .get("/profile-pictures/" + owner.getId()).session(session)).andReturn().getResponse();
            assertEquals(200, response.getStatus());
            assertEquals("image/png", response.getContentType());
            assertArrayEquals(buffer.toByteArray(), response.getContentAsByteArray());
            assertEquals("no-store", response.getHeader("Cache-Control"));
            login(other, "USER"); blocking.blockUser(owner.getId());
            var blockedSession = new org.springframework.mock.web.MockHttpSession();
            blockedSession.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            SecurityContextHolder.clearContext();
            assertEquals(404, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .get("/profile-pictures/" + owner.getId()).session(blockedSession)).andReturn().getResponse().getStatus());
            SecurityContextHolder.clearContext();
            assertNotEquals(200, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .get("/profile-pictures/" + owner.getId())).andReturn().getResponse().getStatus());
        } finally { login(owner, "USER"); images.delete(reference); }
    }
}

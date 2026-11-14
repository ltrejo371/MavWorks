package com.mavworks;

import com.mavworks.model.PasswordResetToken;
import com.mavworks.model.User;
import com.mavworks.repository.PasswordResetTokenRepository;
import com.mavworks.repository.UserRepository;
import com.mavworks.service.PasswordResetEmailSender;
import com.mavworks.service.PasswordResetService;
import java.time.Instant;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "vaadin.launch-browser=false", "vaadin.devserver.enabled=false",
        "mavworks.seed-development-terms=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false", "spring.datasource.url=jdbc:h2:mem:password-reset-test;DB_CLOSE_DELAY=-1"
})
@Transactional
class PasswordResetWorkflowTest {
    @Autowired PasswordResetService resets;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired PasswordEncoder passwords;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    PasswordResetEmailSender mail;

    @Test
    void resetChangesPasswordAndConsumesSingleUseToken() {
        User user = createUser();
        resets.request("  RESET@MAVS.UTA.EDU ");
        var link = captureLink();
        String token = tokenFrom(link);
        assertFalse(tokens.existsById(token));
        assertEquals(1, tokens.count());
        assertTrue(link.startsWith("http://localhost:8080/reset-password?token="));

        assertThrows(IllegalArgumentException.class, () -> resets.reset(token, "new-password", "mismatch"));
        resets.reset(token, "new-password", "new-password");
        assertTrue(passwords.matches("new-password", users.findById(user.getId()).orElseThrow().getHashedPassword()));
        assertThrows(IllegalArgumentException.class,
                () -> resets.reset(token, "another-password", "another-password"));
    }

    @Test
    void unknownEmailDoesNotSendAndExpiredLinkIsRejected() {
        resets.request("missing@mavs.uta.edu");
        verifyNoInteractions(mail);
        createUser();
        resets.request("reset@mavs.uta.edu");
        String token = tokenFrom(captureLink());
        PasswordResetToken stored = tokens.findAll().getFirst();
        ReflectionTestUtils.setField(stored, "expiresAt", Instant.now().minusSeconds(1));
        tokens.flush();
        assertThrows(IllegalArgumentException.class,
                () -> resets.reset(token, "new-password", "new-password"));
    }

    @Test
    void newerResetInvalidatesPreviousLink() {
        createUser();
        resets.request("reset@mavs.uta.edu");
        String first = tokenFrom(captureLink());
        resets.request("reset@mavs.uta.edu");
        String second = tokenFrom(captureLink());
        assertNotEquals(first, second);
        assertThrows(IllegalArgumentException.class,
                () -> resets.reset(first, "new-password", "new-password"));
        resets.reset(second, "new-password", "new-password");
    }

    private User createUser() {
        User user = new User();
        user.setUsername("reset-user");
        user.setEmail("reset@mavs.uta.edu");
        user.setHashedPassword(passwords.encode("old-password"));
        return users.saveAndFlush(user);
    }

    private String captureLink() {
        var link = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).send(eq("reset@mavs.uta.edu"), link.capture());
        return link.getValue();
    }

    private static String tokenFrom(String link) {
        var matcher = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)").matcher(link);
        assertTrue(matcher.find());
        return matcher.group(1);
    }
}

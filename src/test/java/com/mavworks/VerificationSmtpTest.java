package com.mavworks;

import com.mavworks.service.*;
import com.mavworks.repository.*;
import com.mavworks.model.TermsOfService;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

/** Sends through the real SMTP adapter to a loopback-only receiver; no external email is sent. */
@SpringBootTest(properties = {"vaadin.launch-browser=false", "vaadin.devserver.enabled=false",
        "mavworks.seed-development-terms=false", "spring.datasource.url=jdbc:h2:mem:verification-smtp;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false", "spring.mail.properties.mail.smtp.starttls.required=false",
        "spring.mail.properties.mail.smtp.ssl.enable=false", "mavworks.mail.from=no-reply@example.test"})
class VerificationSmtpTest {
    static final ServerSocket smtp;
    static final ExecutorService worker = Executors.newSingleThreadExecutor();
    static final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    static {
        try { smtp = new ServerSocket(0, 1, InetAddress.getLoopbackAddress()); }
        catch (IOException ex) { throw new ExceptionInInitializerError(ex); }
        worker.submit(() -> {
            try (var socket = smtp.accept();
                    var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    var writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII), true)) {
                socket.setSoTimeout(30000);
                writer.print("220 localhost SMTP ready\r\n"); writer.flush();
                boolean data = false; var body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (data) {
                        if (line.equals(".")) { messages.add(body.toString()); data = false; writer.print("250 Accepted\r\n"); }
                        else body.append(line).append('\n');
                    } else if (line.startsWith("EHLO") || line.startsWith("HELO")) writer.print("250 localhost\r\n");
                    else if (line.equals("DATA")) { data = true; writer.print("354 End with dot\r\n"); }
                    else if (line.equals("QUIT")) { writer.print("221 Bye\r\n"); writer.flush(); break; }
                    else writer.print("250 OK\r\n");
                    writer.flush();
                }
            } catch (IOException ex) { if (!smtp.isClosed()) throw new UncheckedIOException(ex); }
        });
    }
    @DynamicPropertySource static void mail(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> smtp.getInetAddress().getHostAddress());
        registry.add("spring.mail.port", smtp::getLocalPort);
    }
    @AfterAll static void close() throws Exception { smtp.close(); worker.shutdownNow(); }
    @Autowired AccountService accounts;
    @Autowired TermsOfServiceRepository terms;
    @Autowired UserRepository users;
    @Test void deliveredCodeCreatesAccountThroughRealSmtp() throws Exception {
        var tos = new TermsOfService(); tos.setVersion("smtp-test"); tos.setContent("Test terms"); tos.setEffectiveAt(Instant.now().minusSeconds(1));
        terms.saveAndFlush(tos);
        var token = accounts.requestVerification("smtp-student", "smtp-student@mavs.uta.edu", "Testing-password-123", "Testing-password-123", tos.getId());
        var message = messages.poll(10, TimeUnit.SECONDS); assertNotNull(message, "SMTP server must receive the email");
        assertTrue(message.contains("Subject: Verify your MavWorks email"));
        assertTrue(message.contains("To: smtp-student@mavs.uta.edu"));
        var match = java.util.regex.Pattern.compile("verification code is: ([0-9]{6})").matcher(message);
        assertTrue(match.find()); assertFalse(users.existsByUsername("smtp-student"));
        accounts.register("smtp-student", "smtp-student@mavs.uta.edu", "Testing-password-123", "Testing-password-123", tos.getId(), token, match.group(1));
        assertTrue(users.existsByUsername("smtp-student"));
    }
}

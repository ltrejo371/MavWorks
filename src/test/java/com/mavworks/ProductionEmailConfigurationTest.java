package com.mavworks;

import com.mavworks.security.ProductionEmailConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;

class ProductionEmailConfigurationTest {
    private ApplicationContextRunner production() {
        return new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(ProductionEmailConfiguration.class)
                .withPropertyValues("spring.profiles.active=prod", "SMTP_HOST=smtp.example.test",
                        "SMTP_USERNAME=test-user", "SMTP_PASSWORD=test-only", "MAIL_FROM=sender@example.test");
    }
    @Test void hostedSettingsOverrideLocalConfigurationAndKeepContextPath() {
        production().withPropertyValues("MAVWORKS_PUBLIC_BASE_URL=https://market.example.test/mavworks")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals("smtp.example.test", context.getEnvironment().getProperty("spring.mail.host"));
                    assertEquals("https://market.example.test/mavworks", context.getEnvironment().getProperty("mavworks.public-base-url"));
                    assertEquals("true", context.getEnvironment().getProperty("server.servlet.session.cookie.secure"));
                    assertNull(context.getEnvironment().getProperty("spring.config.import"));
                });
    }
    @Test void productionRequiresPublicHttpsUrlAndMailCredentials() {
        production().run(context -> assertNotNull(context.getStartupFailure()));
        for (String url : java.util.List.of("http://market.example.test", "http://localhost:8080", "https://localhost", "https://127.0.0.1")) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ProductionEmailConfiguration(url, "host", "user", "test-only", "sender@example.test"));
        }
        assertThrows(IllegalArgumentException.class, () -> new ProductionEmailConfiguration(
                "https://market.example.test", "host", "user", "", "sender@example.test"));
    }
}

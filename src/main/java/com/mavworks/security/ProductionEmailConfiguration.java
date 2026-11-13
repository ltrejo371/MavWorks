package com.mavworks.security;

import java.net.URI;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Fail at startup instead of emailing broken localhost links from the hosted app. */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionEmailConfiguration {
    public ProductionEmailConfiguration(
            @Value("${mavworks.public-base-url}") String publicUrl,
            @Value("${spring.mail.host}") String host,
            @Value("${spring.mail.username}") String username,
            @Value("${spring.mail.password}") String password,
            @Value("${mavworks.mail.from}") String from) {
        URI uri;
        try { uri = URI.create(publicUrl); }
        catch (IllegalArgumentException ex) { throw invalidUrl(); }
        String domain = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equals(uri.getScheme()) || domain.isBlank() || domain.equals("localhost")
                || domain.endsWith(".localhost") || domain.startsWith("127.") || domain.contains(":")
                || domain.equals("0.0.0.0") || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw invalidUrl();
        }
        if (host.isBlank() || username.isBlank() || password.isBlank() || from.isBlank()) {
            throw new IllegalArgumentException("Production email requires SMTP_HOST, SMTP_USERNAME, SMTP_PASSWORD, and MAIL_FROM.");
        }
    }
    private static IllegalArgumentException invalidUrl() {
        return new IllegalArgumentException("Set MAVWORKS_PUBLIC_BASE_URL to the hosted HTTPS address, including any context path; localhost is not allowed in production.");
    }
}

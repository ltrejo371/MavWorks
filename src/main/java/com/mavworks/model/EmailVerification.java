package com.mavworks.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

/** Pending proof of email ownership, not an account. Never stores a plaintext code or password. */
@Entity
@Table(name = "email_verifications")
public class EmailVerification {
    @Id @Column(length = 254) private String email;
    @Column(nullable = false, unique = true) private UUID token;
    @Column(nullable = false) private String codeHash;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false) private Instant sentAt;
    @Column(nullable = false) private Instant windowStartedAt;
    @Column(nullable = false) private int sends;
    @Column(nullable = false) private int attempts;
    private Instant consumedAt;

    protected EmailVerification() {}
    public EmailVerification(String email) { this.email = email; }
    public void issue(String hash, Instant now) {
        if (sentAt != null && now.isBefore(sentAt.plusSeconds(60)))
            throw new IllegalArgumentException("Please wait 60 seconds before requesting another code.");
        if (windowStartedAt == null || !now.isBefore(windowStartedAt.plus(Duration.ofHours(1)))) {
            windowStartedAt = now; sends = 0;
        }
        if (sends >= 5) throw new IllegalArgumentException("Too many verification emails requested. Please try again in an hour.");
        token = UUID.randomUUID(); codeHash = hash; sentAt = now;
        expiresAt = now.plus(Duration.ofMinutes(10)); attempts = 0; consumedAt = null; sends++;
    }
    public boolean canVerify(Instant now) { return consumedAt == null && now.isBefore(expiresAt) && attempts < 5; }
    public void recordFailure() { attempts++; }
    public void consume(Instant now) { consumedAt = now; }
    public String getEmail() { return email; }
    public UUID getToken() { return token; }
    public String getCodeHash() { return codeHash; }
    public int getAttempts() { return attempts; }
    public Instant getConsumedAt() { return consumedAt; }
}

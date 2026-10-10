package com.mavworks.model;

import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name = "terms_of_service")
public class TermsOfService {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String version;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private Instant effectiveAt;

    public TermsOfService() {
    }

    public Long getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(Instant effectiveAt) {
        this.effectiveAt = effectiveAt;
    }
}

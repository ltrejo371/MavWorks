package com.mavworks.model;

import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name = "tos_agreements")
public class TOSAgreement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terms_id", nullable = false)
    private TermsOfService terms;

    @Column(nullable = false)
    private Instant acceptedAt = Instant.now();

    public TOSAgreement() {
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public TermsOfService getTerms() {
        return terms;
    }

    public void setTerms(TermsOfService terms) {
        this.terms = terms;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }
}

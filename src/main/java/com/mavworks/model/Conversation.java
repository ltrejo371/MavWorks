package com.mavworks.model;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** A buyer's message conversation about a provider's listing. */
@Entity
@Table(name = "conversations")
@org.hibernate.annotations.Check(constraints = "sale_confirmed_at is null or closed_at is null or closed_at >= sale_confirmed_at")
public class Conversation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User buyer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Listing listing;
    private Instant saleConfirmedAt;
    private Instant closedAt;

    protected Conversation() {}

    public Conversation(User buyer, Listing listing) {
        if (buyer == null || buyer.getId() == null || listing == null || listing.getId() == null
                || buyer.getId().equals(listing.getOwner().getId())) {
            throw new IllegalArgumentException("A conversation requires a buyer and another user's listing.");
        }
        this.buyer = buyer;
        this.listing = listing;
    }

    public void confirmSale(User provider) {
        if (provider == null || !listing.getOwner().getId().equals(provider.getId())) {
            throw new IllegalArgumentException("Only the provider can confirm this sale.");
        }
        if (closedAt != null) throw new IllegalArgumentException("This conversation is already closed.");
        if (saleConfirmedAt == null) saleConfirmedAt = Instant.now();
    }

    public void closeConversation() {
        if (closedAt == null) closedAt = Instant.now();
    }

    public boolean qualifiesForReview(User reviewer) {
        return reviewer != null && buyer.getId().equals(reviewer.getId())
                && !buyer.getId().equals(listing.getOwner().getId())
                && saleConfirmedAt != null && closedAt != null && !closedAt.isBefore(saleConfirmedAt);
    }

    public Long getId() { return id; }
    public boolean hasParticipant(User user) {
        return user != null && user.getId() != null && (buyer.getId().equals(user.getId())
                || listing.getOwner().getId().equals(user.getId()));
    }
    public User getBuyer() { return buyer; }
    public Listing getListing() { return listing; }
    public Instant getSaleConfirmedAt() { return saleConfirmedAt; }
    public Instant getClosedAt() { return closedAt; }
}

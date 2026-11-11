package com.mavworks.model;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "bookings")
@org.hibernate.annotations.Check(constraints = "end_at > start_at")
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User buyer;

    @Column(name = "start_at", nullable = false, updatable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false, updatable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Booking() {}

    public Booking(User buyer, Listing listing, Instant startAt, Instant endAt) {
        if (buyer == null || buyer.getId() == null || listing == null || listing.getId() == null
                || buyer.getId().equals(listing.getOwner().getId())) {
            throw new IllegalArgumentException("A booking requires a buyer and another user's service.");
        }
        if (listing.getType() != ListingType.SERVICE) {
            throw new IllegalArgumentException("Only services can be booked.");
        }
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Choose a valid appointment time.");
        }
        this.buyer = buyer;
        this.listing = listing;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public void approve() {
        requirePending();
        status = BookingStatus.APPROVED;
    }

    public void decline() {
        requirePending();
        status = BookingStatus.DECLINED;
    }

    public void cancel() {
        if (status != BookingStatus.PENDING && status != BookingStatus.APPROVED) {
            throw new IllegalArgumentException("This booking can no longer be cancelled.");
        }
        status = BookingStatus.CANCELLED;
    }

    private void requirePending() {
        if (status != BookingStatus.PENDING) {
            throw new IllegalArgumentException("This booking request has already been handled.");
        }
    }

    public Long getId() { return id; }
    public Listing getListing() { return listing; }
    public User getBuyer() { return buyer; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public BookingStatus getStatus() { return status; }
}

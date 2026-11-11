package com.mavworks.model;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "availability_slots",
        uniqueConstraints = @UniqueConstraint(name = "uq_availability_listing_start", columnNames = {"listing_id", "start_at"}))
@org.hibernate.annotations.Check(constraints = "end_at > start_at")
public class AvailabilitySlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Listing listing;

    @Column(name = "start_at", nullable = false, updatable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false, updatable = false)
    private Instant endAt;

    protected AvailabilitySlot() {}

    public AvailabilitySlot(Listing listing, Instant startAt, Instant endAt) {
        if (listing == null || listing.getId() == null || listing.getType() != ListingType.SERVICE
                || startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Choose a valid available time for a service.");
        }
        this.listing = listing;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public Long getId() { return id; }
    public Listing getListing() { return listing; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
}

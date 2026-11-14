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

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.REQUESTED;

    // Creation time of the current request, reset when a reschedule is proposed.
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Conversation conversation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User requester;

    protected Booking() {}
    public Booking(Conversation conversation, User requester, Instant start, Instant end) {
        this(conversation.getBuyer(), conversation.getListing(), start, end);
        if (!conversation.hasParticipant(requester)) throw new IllegalArgumentException("Only participants can request appointments.");
        this.conversation = conversation;
        this.requester = requester;
    }
    public Conversation getConversation() { return conversation; }
    public User getRequester() { return requester == null ? buyer : requester; }
    public boolean isExpired(Instant now) {
        return status == BookingStatus.REQUESTED && (!startAt.isAfter(now) || !createdAt.plus(java.time.Duration.ofHours(48)).isAfter(now));
    }
    public void expire() { requirePending(); status = BookingStatus.EXPIRED; }
    public void complete() {
        if (status != BookingStatus.CONFIRMED) throw new IllegalArgumentException("Only confirmed appointments can be completed.");
        status = BookingStatus.COMPLETED;
    }

    private Booking(User buyer, Listing listing, Instant startAt, Instant endAt) {
        if (buyer == null || buyer.getId() == null || listing == null || listing.getId() == null
                || buyer.getId().equals(listing.getOwner().getId())) {
            throw new IllegalArgumentException("An appointment requires a buyer and another user's listing.");
        }

        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Choose a valid appointment time.");
        }
        this.buyer = buyer;
        this.listing = listing;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public void reschedule(User requester, Instant start, Instant end) {
        if (status != BookingStatus.CONFIRMED || start == null || end == null || !end.isAfter(start))
            throw new IllegalArgumentException("Choose a valid time for a confirmed appointment.");
        if (!conversation.hasParticipant(requester))
            throw new IllegalArgumentException("Only participants can request appointments.");
        startAt = start;
        endAt = end;
        this.requester = requester;
        createdAt = Instant.now();
        status = BookingStatus.REQUESTED;
    }

    public void approve() {
        requirePending();
        status = BookingStatus.CONFIRMED;
    }

    public void cancel() {
        if (status != BookingStatus.REQUESTED && status != BookingStatus.CONFIRMED) {
            throw new IllegalArgumentException("This booking can no longer be cancelled.");
        }
        status = BookingStatus.CANCELLED;
    }

    private void requirePending() {
        if (status != BookingStatus.REQUESTED) {
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

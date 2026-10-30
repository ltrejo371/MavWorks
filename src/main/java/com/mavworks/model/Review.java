package com.mavworks.model;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(
        name = "reviews_author_listing_unique", columnNames = {"author_id", "listing_id"}))
@Check(constraints = "rating between 1 and 5")
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User author;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Listing listing;
    @Column(nullable = false, updatable = false)
    private int rating;
    @Column(length = 2000, updatable = false)
    private String comment;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Review() {}

    Review(User author, Conversation conversation, Integer rating, String comment) {
        if (conversation == null || !conversation.qualifiesForReview(author)) {
            throw new IllegalArgumentException("A review can only be left after a completed sale.");
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("A rating from 1 to 5 is required.");
        }
        String text = comment == null ? "" : comment.trim();
        if (text.length() > 2000) throw new IllegalArgumentException("Comments must be at most 2000 characters.");
        this.author = author;
        this.listing = conversation.getListing();
        this.rating = rating;
        this.comment = text;
    }

    public Long getId() { return id; }
    public User getAuthor() { return author; }
    public Listing getListing() { return listing; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
}

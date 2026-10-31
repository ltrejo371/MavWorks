package com.mavworks.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
@Entity
@Table(name = "listings")
public class Listing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User owner;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant expiresAt = Instant.now().plus(java.time.Duration.ofDays(30));
    @Column(nullable = false)
    private long viewCount;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ListingStyle style = ListingStyle.CLASSIC;

    public Instant getExpiresAt() { return expiresAt; }
    public long getViewCount() { return viewCount; }
    public ListingStyle getStyle() { return style; }
    public void setStyle(ListingStyle style) {
        if (style == null) throw new IllegalArgumentException("Select a listing style.");
        this.style = style;
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingCategory category = ListingCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingStatus status = ListingStatus.ACTIVE;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "listing_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    @OrderBy("displayOrder ASC")
    private List<ListingImage> images = new ArrayList<>();

    public Listing() {
    }

    public void markSold() {
        if (status == ListingStatus.REMOVED) throw new IllegalArgumentException("A removed listing cannot be changed.");
        status = ListingStatus.SOLD;
    }

    public void renew() {
        if (status == ListingStatus.SOLD || status == ListingStatus.REMOVED)
            throw new IllegalArgumentException("Sold or removed listings cannot be renewed.");
        status = ListingStatus.ACTIVE;
        expiresAt = Instant.now().plus(java.time.Duration.ofDays(30));
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public ListingType getType() {
        return type;
    }

    public void setType(ListingType type) {
        this.type = type;
    }

    public ListingCategory getCategory() {
        return category;
    }

    public void setCategory(ListingCategory category) {
        this.category = category;
    }

    public ListingStatus getStatus() {
        return status == ListingStatus.ACTIVE && !expiresAt.isAfter(Instant.now()) ? ListingStatus.EXPIRED : status;
    }

    public void setStatus(ListingStatus status) {
        this.status = status;
    }

    public List<ListingImage> getImages() {
        return images;
    }

    public void setImages(List<ListingImage> images) {
        this.images = images;
    }
}

package com.mavworks.model;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class User implements UserActions {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String hashedPassword;

    @Embedded
    private Profile profile = new Profile();

    public User() {
    }

    /** Builds a validated listing; ListingService authenticates and persists it. */
    @Override
    public Listing createListing(com.mavworks.dto.CreateListingRequest request) {
        if (request == null) throw new IllegalArgumentException("Provide listing information.");

        java.util.List<String> errors = new java.util.ArrayList<>();

        if (request.name() == null || request.name().isBlank()) errors.add("Enter a listing name.");
        else if (request.name().trim().length() > 255) errors.add("Name must be at most 255 characters.");

        if (request.description() == null || request.description().isBlank()) errors.add("Enter a description.");
        else if (request.description().trim().length() > 4000) errors.add("Description must be at most 4000 characters.");

        if (request.type() == null) errors.add("Select Product or Service.");
        if (request.price() == null || request.price().signum() < 0
                || request.price().compareTo(new java.math.BigDecimal("999999.99")) > 0
                || request.price().stripTrailingZeros().scale() > 2) {
            errors.add("Enter a price from 0 to 999,999.99 with at most two decimal places.");
        }

        if (request.imageReferences().size() > 5) errors.add("Select at most five images.");
        if (request.imageReferences().stream().anyMatch(ref -> ref == null || ref.isBlank())) {
            errors.add("An image could not be uploaded. Select it again.");
        }

        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join(" ", errors));

        Listing listing = new Listing();
        listing.setOwner(this);
        listing.setName(request.name().trim());
        listing.setDescription(request.description().trim());
        listing.setPrice(request.price().setScale(2));
        listing.setType(request.type());
        listing.setCategory(request.category() == null ? ListingCategory.OTHER : request.category());
        listing.setStatus(ListingStatus.ACTIVE);

        for (String reference : request.imageReferences()) {
            ListingImage image = new ListingImage();
            image.setStorageReference(reference);
            image.setDisplayOrder(listing.getImages().size());
            listing.getImages().add(image);
        }
        return listing;
    }

    @Override
    public void deleteListing() {
        // TODO: Implement.
    }

    @Override
    public void sendMessage() {
        // TODO: Implement.
    }

    @Override
    public void leaveReview() {
        // TODO: Implement.
    }

    @Override
    public void blockUser() {
        // TODO: Implement.
    }

    @Override
    public void reportUser() {
        // TODO: Implement.
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getHashedPassword() {
        return hashedPassword;
    }

    public void setHashedPassword(String hashedPassword) {
        this.hashedPassword = hashedPassword;
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }
}

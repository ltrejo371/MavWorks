package com.mavworks.service;

import com.mavworks.dto.*;
import com.mavworks.model.User;
import com.mavworks.repository.*;
import com.mavworks.security.CurrentUser;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReviewService {
    public record ProviderProfile(PublicProfileData profile, List<ReviewSummary> reviews) {}
    private final CurrentUser currentUser;
    private final UserRepository users;
    private final ConversationRepository conversations;
    private final ReviewRepository reviews;

    public ReviewService(CurrentUser currentUser, UserRepository users,
            ConversationRepository conversations, ReviewRepository reviews) {
        this.currentUser = currentUser;
        this.users = users;
        this.conversations = conversations;
        this.reviews = reviews;
    }

    private User author(boolean lock) {
        String username = currentUser.username()
                .orElseThrow(() -> new AccessDeniedException("Sign in to leave a review."));
        return (lock ? users.findForReview(username) : users.findByUsername(username))
                .orElseThrow(() -> new AccessDeniedException("Sign in to leave a review."));
    }

    public Optional<ProviderProfile> getProviderProfile(Long providerId) {
        if (providerId == null) return Optional.empty();
        return users.findById(providerId).map(user -> {
            var p = user.getProfile();
            var publicData = new PublicProfileData(user.getId(), user.getUsername(),
                    p == null ? null : p.getName(), p == null ? null : p.getBio(),
                    p == null ? null : p.getProfilePictureUrl(), p == null ? null : p.getContactInformation());
            return new ProviderProfile(publicData, reviews.findPublicReviews(providerId));
        });
    }

    public List<ReviewableListing> getReviewableListings(Long providerId) {
        var user = author(false);
        if (providerId == null || providerId.equals(user.getId())) return List.of();
        var reviewed = new HashSet<>(reviews.findReviewedListingIds(user.getId()));
        return conversations.findCompletedSales(user.getId(), providerId).stream()
                .filter(c -> c.qualifiesForReview(user)).map(c -> c.getListing())
                .filter(l -> !reviewed.contains(l.getId()))
                .map(l -> new ReviewableListing(l.getId(), l.getName())).distinct().toList();
    }

    @Transactional
    public Long leaveReview(Long providerId, CreateReviewRequest request) {
        // Serialize submissions by author; the database unique constraint also protects duplicates.
        var user = author(true);
        if (request == null || request.listingId() == null) {
            throw new IllegalArgumentException("Select the listing you are reviewing.");
        }
        if (providerId == null || providerId.equals(user.getId())) {
            throw new IllegalArgumentException("A review can only be left after a completed sale with another provider.");
        }
        var conversation = conversations.findCompletedSales(user.getId(), providerId).stream()
                .filter(c -> c.getListing().getId().equals(request.listingId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A review can only be left after a completed sale for this listing."));
        if (reviews.existsByAuthorIdAndListingId(user.getId(), request.listingId())) {
            throw new IllegalArgumentException("You have already reviewed this listing.");
        }
        return reviews.saveAndFlush(user.leaveReview(conversation, request.rating(), request.comment())).getId();
    }
}

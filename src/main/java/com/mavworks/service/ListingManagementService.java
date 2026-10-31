package com.mavworks.service;

import com.mavworks.model.*;
import com.mavworks.repository.*;
import com.mavworks.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListingManagementService {
    private final CurrentUser current;
    private final UserRepository users;
    private final ListingRepository listings;
    public ListingManagementService(CurrentUser current, UserRepository users, ListingRepository listings) {
        this.current = current; this.users = users; this.listings = listings;
    }
    private User user() {
        return current.username().flatMap(users::findByUsername).orElseThrow(() -> new AccessDeniedException("Sign in to manage listings."));
    }
    private Listing owned(Long id) {
        User user = user();
        if (id == null) throw new IllegalArgumentException("Select a listing.");
        var listing = listings.findForManagement(id).orElseThrow(() -> new IllegalArgumentException("Listing not found."));
        if (!listing.getOwner().getId().equals(user.getId())) throw new AccessDeniedException("Only the provider can change this listing.");
        if (listing.getStatus() == ListingStatus.REMOVED) throw new IllegalArgumentException("This listing was removed by an administrator.");
        return listing;
    }
    @Transactional public void markSold(Long id) { owned(id).markSold(); }
    @Transactional public void renew(Long id) { owned(id).renew(); }
    @Transactional public void changeStyle(Long id, ListingStyle style) { owned(id).setStyle(style); }
    @Transactional public void recordView(Long id) { if (id != null) listings.recordView(id, user().getId()); }
    private void admin() {
        user();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN")))
            throw new AccessDeniedException("Only administrators can moderate listings.");
    }
    public record ModerationListing(Long id, String name, String owner, ListingStatus status, java.time.Instant posted, java.time.Instant expires) {}
    @Transactional(readOnly = true) public org.springframework.data.domain.Page<ModerationListing> moderationQueue(int page) {
        admin();
        return listings.findAllByOrderByCreatedAtDesc(org.springframework.data.domain.PageRequest.of(page, 50))
                .map(l -> new ModerationListing(l.getId(), l.getName(), l.getOwner().getUsername(), l.getStatus(), l.getCreatedAt(), l.getExpiresAt()));
    }
    @Transactional public void remove(Long id) {
        admin();
        if (id == null) throw new IllegalArgumentException("Select a listing.");
        listings.findForManagement(id).orElseThrow(() -> new IllegalArgumentException("Listing not found.")).setStatus(ListingStatus.REMOVED);
    }
}

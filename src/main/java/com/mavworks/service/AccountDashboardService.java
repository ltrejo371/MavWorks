package com.mavworks.service;

import com.mavworks.dto.ListingSummary;
import com.mavworks.repository.ListingRepository;
import com.mavworks.repository.UserRepository;
import com.mavworks.security.CurrentUser;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves account data from the session, never from a client-provided user ID. */
@Service
public class AccountDashboardService {
    public record DashboardData(String username, String email, String name, String bio,
            String contactInformation, List<ListingSummary> listings) {}

    private final CurrentUser currentUser;
    private final UserRepository users;
    private final ListingRepository listings;

    public AccountDashboardService(CurrentUser currentUser, UserRepository users, ListingRepository listings) {
        this.currentUser = currentUser;
        this.users = users;
        this.listings = listings;
    }

    @Transactional(readOnly = true)
    public DashboardData load() {
        var user = currentUser.username().flatMap(users::findByUsername)
                .orElseThrow(() -> new AccessDeniedException("Sign in to view your account."));
        var profile = user.getProfile();
        var ownedListings = listings.findByOwnerId(user.getId()).stream()
                .map(listing -> new ListingSummary(listing.getId(), listing.getName(),
                        listing.getDescription(), listing.getPrice(), listing.getCategory(),
                        listing.getType())).toList();
        return new DashboardData(user.getUsername(), user.getEmail(),
                profile == null ? null : profile.getName(),
                profile == null ? null : profile.getBio(),
                profile == null ? null : profile.getContactInformation(), ownedListings);
    }
}

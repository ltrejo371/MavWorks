package com.mavworks.service;

import com.mavworks.model.ListingCategory;
import com.mavworks.model.ListingStatus;
import com.mavworks.model.ListingType;
import java.math.BigDecimal;
import java.time.Instant;
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

    public record OwnerListing(
            Long id,
            String name,
            String description,
            BigDecimal price,
            ListingCategory category,
            ListingType type,
            ListingStatus status,
            Instant expiresAt) {}

    public record DashboardData(String username, String email, String name, String bio,
            String contactInformation, List<OwnerListing> listings, List<com.mavworks.dto.AccountWarning> warnings,
            Long userId, boolean hasPicture, List<SubmittedReport> submittedReports, List<String> blockedAccounts) {}
    public record SubmittedReport(Long id, String reportedUser, com.mavworks.model.ReportCategory category,
            com.mavworks.model.ReportStatus status, java.time.Instant createdAt) {}

    private final CurrentUser currentUser;
    private final UserRepository users;
    private final ListingRepository listings;
    private final com.mavworks.repository.ReportRepository reports;
    private final com.mavworks.repository.UserBlockRepository blocks;

    public AccountDashboardService(CurrentUser currentUser, UserRepository users, ListingRepository listings,
            com.mavworks.repository.ReportRepository reports, com.mavworks.repository.UserBlockRepository blocks) {
        this.currentUser = currentUser;
        this.users = users;
        this.listings = listings;
        this.reports = reports;
        this.blocks = blocks;
    }

    @Transactional(readOnly = true)
    public DashboardData load() {
        var user = currentUser.username().flatMap(users::findByUsername)
                .orElseThrow(() -> new AccessDeniedException("Sign in to view your account."));
        var profile = user.getProfile();
        var ownedListings = listings.findByOwnerId(user.getId()).stream()
                .map(listing -> new OwnerListing(
                        listing.getId(),
                        listing.getName(),
                        listing.getDescription(),
                        listing.getPrice(),
                        listing.getCategory(),
                        listing.getType(),
                        listing.getStatus(),
                        listing.getExpiresAt()))
                .toList();
        return new DashboardData(user.getUsername(), user.getEmail(),
                profile == null ? null : profile.getName(),
                profile == null ? null : profile.getBio(),
                profile == null ? null : profile.getContactInformation(), ownedListings, reports.findWarnings(user.getId()),
                user.getId(), profile != null && profile.getProfilePictureUrl() != null,
                reports.findByReporterIdOrderByCreatedAtDescIdDesc(user.getId()).stream().map(r -> new SubmittedReport(
                        r.getId(), r.getReportedUser() == null ? "Deleted account" : r.getReportedUser().getUsername(),
                        r.getCategory(), r.getStatus(), r.getCreatedAt())).toList(), blocks.findBlockedUsernames(user.getId()));
    }
}

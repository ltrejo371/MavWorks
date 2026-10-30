package com.mavworks.service;

import com.mavworks.dto.CreateListingRequest;
import com.mavworks.dto.SearchFilter;
import com.mavworks.model.*;
import com.mavworks.repository.*;
import com.mavworks.security.CurrentUser;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ListingServiceImpl implements ListingService {
    private final ListingRepository listings;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final LocalImageStorageService images;

    public ListingServiceImpl(ListingRepository listings, UserRepository users,
            CurrentUser currentUser, LocalImageStorageService images) {
        this.listings = listings;
        this.users = users;
        this.currentUser = currentUser;
        this.images = images;
    }

    private User owner() {
        return currentUser.username().flatMap(users::findByUsername)
                .orElseThrow(() -> new AccessDeniedException("Sign in to manage your listings."));
    }

    @Override
    public Long createListing(CreateListingRequest request) {
        Listing listing = owner().createListing(request);
        request.imageReferences().forEach(images::validateOwnedReference);
        return listings.saveAndFlush(listing).getId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Listing> search(SearchFilter filter) {
        return listings.searchPublished(ListingStatus.ACTIVE,
                filter.keyword() == null ? "" : filter.keyword().trim(), filter.category(), null);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Listing> getListingDetails(Long listingId) {
        var viewer = owner();
        return listings.findById(listingId).filter(l -> l.getStatus() != ListingStatus.REMOVED
                || l.getOwner().getId().equals(viewer.getId()))
                .map(l -> { l.getImages().size(); return l; });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Listing> getMyListings() {
        return listings.findByOwnerId(owner().getId());
    }
}

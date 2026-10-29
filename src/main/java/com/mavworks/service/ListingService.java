package com.mavworks.service;

import com.mavworks.dto.CreateListingRequest;
import com.mavworks.dto.SearchFilter;
import com.mavworks.model.Listing;
import java.util.List;
import java.util.Optional;

/** Listing publication and queries used by the listing views. */
public interface ListingService {
    // Validate input; resolve ownership from authentication, never from the form.
    Long createListing(CreateListingRequest request);
    List<Listing> search(SearchFilter filter);
    Optional<Listing> getListingDetails(Long listingId);
    List<Listing> getMyListings();
}

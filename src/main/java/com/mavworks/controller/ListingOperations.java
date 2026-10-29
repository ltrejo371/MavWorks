package com.mavworks.controller;

import com.mavworks.model.Listing;
import java.util.List;

/** Listing controller operations defined by the UML diagram. */
public interface ListingOperations {
    List<Listing> getActiveListings();

    List<Listing> searchListings();

    void createListing();

    void deleteListing();

    void markListingSold();
}

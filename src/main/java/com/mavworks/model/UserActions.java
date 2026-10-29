package com.mavworks.model;

/** User actions */
public interface UserActions {
    Listing createListing(com.mavworks.dto.CreateListingRequest request);

    void deleteListing();

    void sendMessage();

    void leaveReview();

    void blockUser();

    void reportUser();
}

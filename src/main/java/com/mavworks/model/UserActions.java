package com.mavworks.model;

/** User actions */
public interface UserActions {
    Listing createListing(com.mavworks.dto.CreateListingRequest request);

    void deleteListing();

    Message sendMessage(Conversation conversation, String text);

    Review leaveReview(Conversation conversation, Integer rating, String comment);

    void blockUser();

    void reportUser();
}

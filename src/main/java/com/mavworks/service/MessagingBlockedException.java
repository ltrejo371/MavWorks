package com.mavworks.service;

import org.springframework.security.access.AccessDeniedException;

public class MessagingBlockedException extends AccessDeniedException {
    private final Long listingId;
    public MessagingBlockedException(Long listingId) {
        super("Messaging is unavailable because one of you has blocked the other.");
        this.listingId = listingId;
    }
    public Long getListingId() { return listingId; }
}

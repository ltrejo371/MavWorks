package com.mavworks.dto;

/** Author identity is always resolved from authentication. */
public record CreateReviewRequest(Long listingId, Integer rating, String comment) {}

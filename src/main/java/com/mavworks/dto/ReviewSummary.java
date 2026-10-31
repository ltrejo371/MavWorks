package com.mavworks.dto;

import java.time.Instant;

public record ReviewSummary(Long id, String author, Long listingId, String listingName,
        int rating, String comment, Instant createdAt, Long authorId) {}

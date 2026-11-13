package com.mavworks.dto;

import com.mavworks.model.ListingCategory;

/** A null category means all categories; blank keywords mean no text filter. */
public record SearchFilter(String keyword, ListingCategory category,
        com.mavworks.model.ListingSort sort, java.time.Instant from, java.time.Instant until) {
    public SearchFilter(String keyword, ListingCategory category) {
        this(keyword, category, com.mavworks.model.ListingSort.NEWEST, null, null);
    }
    public SearchFilter {
        if (sort == null) sort = com.mavworks.model.ListingSort.NEWEST;
        if (from == null) from = java.time.Instant.EPOCH;
        if (until == null) until = java.time.Instant.parse("9999-12-31T00:00:00Z");
        if (!from.isBefore(until)) throw new IllegalArgumentException("Start date must be on or before end date.");
    }
}

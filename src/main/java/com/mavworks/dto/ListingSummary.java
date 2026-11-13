package com.mavworks.dto;

import com.mavworks.model.ListingCategory;
import com.mavworks.model.ListingType;
import java.math.BigDecimal;
import java.time.Instant;

/** Public listing fields for the dashboard; excludes account and credential data. */
public record ListingSummary(Long id, String name, String description, BigDecimal price,
        ListingCategory category, ListingType type, String imageReference, long viewCount, Instant createdAt) {
    public ListingSummary(Long id, String name, String description, BigDecimal price,
            ListingCategory category, ListingType type) {
        this(id, name, description, price, category, type, null, 0, null);
    }

    public ListingSummary(Long id, String name, String description, BigDecimal price,
            ListingCategory category, ListingType type, String imageReference) {
        this(id, name, description, price, category, type, imageReference, 0, null);
    }
}

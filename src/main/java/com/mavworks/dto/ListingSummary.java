package com.mavworks.dto;

import com.mavworks.model.ListingCategory;
import com.mavworks.model.ListingType;
import java.math.BigDecimal;

/** Public listing fields for the dashboard; excludes account and credential data. */
public record ListingSummary(Long id, String name, String description, BigDecimal price,
        ListingCategory category, ListingType type, String imageReference) {
    public ListingSummary(Long id, String name, String description, BigDecimal price,
            ListingCategory category, ListingType type) {
        this(id, name, description, price, category, type, null);
    }
}

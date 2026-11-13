package com.mavworks.model;

import org.springframework.data.domain.Sort;

public enum ListingSort {
    NEWEST("Newest first", "createdAt", Sort.Direction.DESC),
    OLDEST("Oldest first", "createdAt", Sort.Direction.ASC),
    PRICE_LOW_TO_HIGH("Price: low to high", "price", Sort.Direction.ASC),
    PRICE_HIGH_TO_LOW("Price: high to low", "price", Sort.Direction.DESC),
    MOST_VIEWED("Most viewed", "viewCount", Sort.Direction.DESC);

    private final String label;
    private final String property;
    private final Sort.Direction direction;
    ListingSort(String label, String property, Sort.Direction direction) {
        this.label = label; this.property = property; this.direction = direction;
    }
    public String label() { return label; }
    public Sort ordering() { return Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "id")); }
}

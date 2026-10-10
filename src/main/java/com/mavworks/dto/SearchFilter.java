package com.mavworks.dto;

import com.mavworks.model.ListingCategory;

/** A null category means all categories; blank keywords mean no text filter. */
public record SearchFilter(String keyword, ListingCategory category) {
}

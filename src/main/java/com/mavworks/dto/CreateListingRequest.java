package com.mavworks.dto;

import com.mavworks.model.ListingCategory;
import com.mavworks.model.ListingType;
import java.math.BigDecimal;
import java.util.List;

/** The service must validate inputs and resolve the owner from the session. */
public record CreateListingRequest(String name, String description, BigDecimal price,
        ListingType type, ListingCategory category, List<String> imageReferences,
        Integer appointmentDurationMinutes) {
    public CreateListingRequest(String name, String description, BigDecimal price,
            ListingType type, ListingCategory category, List<String> imageReferences) {
        this(name, description, price, type, category, imageReferences, 30);
    }

    public CreateListingRequest {
        imageReferences = imageReferences == null ? List.of() : List.copyOf(imageReferences);
        appointmentDurationMinutes = appointmentDurationMinutes == null ? 30 : appointmentDurationMinutes;
    }
}

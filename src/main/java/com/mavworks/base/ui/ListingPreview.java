package com.mavworks.base.ui;

import com.mavworks.dto.ListingSummary;
import com.mavworks.service.LocalImageStorageService;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.server.streams.*;

/** Consistent image presentation with a usable fallback for absent or missing files. */
public final class ListingPreview {
    private ListingPreview() {}
    public static Div image(String reference, String title, LocalImageStorageService storage) {
        Div frame = new Div(new Span("No image available")); frame.addClassName("listing-picture");
        if (reference != null && !reference.isBlank()) {
            Image image = new Image(DownloadHandler.fromInputStream(e -> {
                try {
                    return new DownloadResponse(storage.open(reference), reference,
                            reference.endsWith("png") ? "image/png" : "image/jpeg", -1);
                } catch (java.io.IOException | IllegalArgumentException | org.springframework.security.access.AccessDeniedException ex) {
                    return DownloadResponse.error(404);
                }
            }), title);
            image.getElement().setAttribute("loading", "lazy");
            image.getElement().addEventListener("error", e -> image.setVisible(false));
            frame.add(image);
        }
        return frame;
    }
    public static Anchor card(ListingSummary listing, LocalImageStorageService storage) {
        Anchor card = new Anchor("listings/" + listing.id(), ""); card.addClassName("profile-listing-card");
        Span price = new Span(java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(listing.price()));
        price.addClassName("listing-price-small");
        Div info = new Div(new H3(listing.name()), price, new Paragraph(label(listing.type().name())));
        card.add(image(listing.imageReference(), listing.name(), storage), info); return card;
    }
    public static String label(String value) {
        String text = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}

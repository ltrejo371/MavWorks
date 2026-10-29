package com.mavworks.listing;

import com.mavworks.base.ui.ViewTitle;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route("listings/:listingId")
@PageTitle("Listing Details")
@PermitAll
public class ListingDetailsView extends VerticalLayout implements com.vaadin.flow.router.BeforeEnterObserver {
    private final com.mavworks.service.ListingService listings;
    private final com.mavworks.service.LocalImageStorageService storage;

    public ListingDetailsView(com.mavworks.service.ListingService listings,
            com.mavworks.service.LocalImageStorageService storage) {
        this.listings = listings;
        this.storage = storage;
    }

    @Override
    public void beforeEnter(com.vaadin.flow.router.BeforeEnterEvent event) {
        removeAll();
        try {
            Long id = Long.valueOf(event.getRouteParameters().get("listingId").orElse(""));
            var result = listings.getListingDetails(id);
            if (result.isEmpty()) {
                add(new Paragraph("Listing not found."));
                return;
            }
            var listing = result.get();
            add(new ViewTitle(listing.getName()),
                    new Paragraph(java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(listing.getPrice())),
                    new Paragraph(listing.getType().name() + " · " + listing.getCategory().name()),
                    new Paragraph(listing.getDescription()));
            for (var image : listing.getImages()) {
                String reference = image.getStorageReference();
                var handler = com.vaadin.flow.server.streams.DownloadHandler.fromInputStream(download -> {
                    try {
                        return new com.vaadin.flow.server.streams.DownloadResponse(storage.open(reference), reference,
                                reference.endsWith("png") ? "image/png" : "image/jpeg", -1);
                    } catch (java.io.IOException ex) {
                        return com.vaadin.flow.server.streams.DownloadResponse.error(404);
                    }
                }
                );
                var display = new com.vaadin.flow.component.html.Image(handler, listing.getName());
                display.setMaxWidth("100%");
                display.setWidth("32rem");
                add(display);
            }
        } catch (NumberFormatException ex) {
            add(new Paragraph("Listing not found."));
        } catch (org.springframework.dao.DataAccessException ex) {
            add(new Paragraph("The listing could not be loaded. Refresh to try again."));
        }
    }
}

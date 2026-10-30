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
    private final com.mavworks.service.MessagingService messages;

    public ListingDetailsView(com.mavworks.service.ListingService listings,
            com.mavworks.service.LocalImageStorageService storage, com.mavworks.service.MessagingService messages) {
        this.listings = listings;
        this.storage = storage;
        this.messages = messages;
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
                    new Paragraph(listing.getDescription()),
                    new com.vaadin.flow.component.html.Anchor("profiles/" + listing.getOwner().getId(), "View provider profile and reviews"));
            Paragraph messageStatus = new Paragraph();
            messageStatus.getElement().setAttribute("role", "status");
            var messageButton = new com.vaadin.flow.component.button.Button("Message");
            messageButton.setDisableOnClick(true);
            messageButton.addClickListener(click -> {
                try {
                    Long conversationId = messages.startConversation(id);
                    getUI().ifPresent(ui -> ui.navigate("messages/" + conversationId));
                } catch (IllegalArgumentException | org.springframework.security.access.AccessDeniedException ex) {
                    messageStatus.setText(ex.getMessage());
                } catch (org.springframework.dao.DataAccessException ex) {
                    messageStatus.setText("The conversation could not be opened. Select Message to try again.");
                } finally { messageButton.setEnabled(true); }
            });
            add(messageButton, messageStatus);
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

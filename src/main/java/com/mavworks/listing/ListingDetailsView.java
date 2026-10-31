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
    private final com.mavworks.service.BlockService blocks;
    private final com.mavworks.service.ReportService reports;

    public ListingDetailsView(com.mavworks.service.ListingService listings,
            com.mavworks.service.LocalImageStorageService storage, com.mavworks.service.MessagingService messages,
            com.mavworks.service.BlockService blocks, com.mavworks.service.ReportService reports) {
        this.listings = listings;
        this.storage = storage;
        this.messages = messages;
        this.blocks = blocks;
        this.reports = reports;
        addClassNames("mw-page", "listing-detail-page");
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
            add(new com.vaadin.flow.component.html.Anchor("", "← Back to marketplace"));
            var gallery = new com.vaadin.flow.component.html.Div(); gallery.addClassName("listing-gallery");
            var hero = new com.vaadin.flow.component.html.Div(); hero.addClassName("listing-hero");
            hero.add(com.mavworks.base.ui.ListingPreview.image(listing.getImages().isEmpty() ? null : listing.getImages().getFirst().getStorageReference(), listing.getName(), storage));
            gallery.add(hero);
            if (listing.getImages().size() > 1) {
                var thumbnails = new com.vaadin.flow.component.html.Div(); thumbnails.addClassName("listing-thumbnails");
                for (int index = 0; index < listing.getImages().size(); index++) {
                    String reference = listing.getImages().get(index).getStorageReference();
                    var thumb = new com.vaadin.flow.component.html.NativeButton();
                    thumb.getElement().setAttribute("aria-label", "View photo " + (index + 1));
                    thumb.add(com.mavworks.base.ui.ListingPreview.image(reference, listing.getName() + " photo " + (index + 1), storage));
                    thumb.addClickListener(e -> { hero.removeAll(); hero.add(com.mavworks.base.ui.ListingPreview.image(reference, listing.getName(), storage)); });
                    thumbnails.add(thumb);
                }
                gallery.add(thumbnails);
            }
            var info = new com.vaadin.flow.component.html.Div(); info.addClassNames("mw-panel", "listing-info");
            var price = new Paragraph(java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(listing.getPrice()));
            price.addClassName("listing-price");
            var tags = new com.vaadin.flow.component.html.Div(); tags.addClassName("listing-tags");
            for (String tag : java.util.List.of(listing.getType().name(), listing.getCategory().name(), listing.getStatus().name())) {
                var badge = new com.vaadin.flow.component.html.Span(com.mavworks.base.ui.ListingPreview.label(tag)); badge.addClassName("mw-badge"); tags.add(badge);
            }
            info.add(tags, new ViewTitle(listing.getName()), price);
            var providerCard = new com.vaadin.flow.component.html.Div(new com.vaadin.flow.component.html.H3("Listed by"));
            providerCard.addClassName("listing-provider");
            blocks.visibleUser(listing.getOwner().getId()).ifPresent(provider -> providerCard.add(
                    new com.mavworks.base.ui.UserOptions(provider.id(), provider.username(), blocks, reports,
                            () -> beforeEnter(event)),
                    new com.vaadin.flow.component.html.Anchor("profiles/" + provider.id(), "View profile and reviews")));
            info.add(providerCard);
            Paragraph messageStatus = new Paragraph();
            messageStatus.getElement().setAttribute("role", "status");
            var messageButton = new com.vaadin.flow.component.button.Button("Message");
            messageButton.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.PRIMARY);
            messageButton.setWidthFull();
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
            info.add(messageButton, messageStatus);
            var overview = new com.vaadin.flow.component.html.Div(gallery, info); overview.addClassName("listing-overview");
            var description = new Paragraph(listing.getDescription()); description.addClassName("mw-prose");
            var about = new com.vaadin.flow.component.html.Div(new com.vaadin.flow.component.html.H2("About this listing"), description);
            about.addClassName("mw-panel"); add(overview, about);
        } catch (NumberFormatException ex) {
            add(new Paragraph("Listing not found."));
        } catch (org.springframework.dao.DataAccessException ex) {
            add(new Paragraph("The listing could not be loaded. Refresh to try again."));
        }
    }
}

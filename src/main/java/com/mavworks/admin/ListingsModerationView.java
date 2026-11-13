package com.mavworks.admin;

import com.mavworks.model.ListingStatus;
import com.mavworks.service.ListingManagementService;
import com.mavworks.service.ListingManagementService.ModerationFilter;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;

@Route("admin/listings")
@PageTitle("Moderate listings")
@Menu(order = 3.1, icon = "vaadin:list", title = "Moderate listings")
@RolesAllowed("ADMIN")
public class ListingsModerationView extends VerticalLayout implements BeforeEnterObserver {
    private final ListingManagementService service;
    private final ComboBox<ModerationFilter> filter = new ComboBox<>("Listings to review");
    private final Div results = new Div();
    private final Paragraph feedback = new Paragraph();
    private final Button previous = new Button("Previous");
    private final Button next = new Button("Next");
    private int page;

    public ListingsModerationView(ListingManagementService service) {
        this.service = service;
        addClassName("mw-page");
        filter.setItems(ModerationFilter.values());
        filter.setItemLabelGenerator(ModerationFilter::label);
        filter.setValue(ModerationFilter.OUTDATED_INACTIVE);
        filter.addValueChangeListener(e -> { if (e.getValue() != null) { page = 0; load(); } });
        previous.addClickListener(e -> { if (page > 0) { page--; load(); } });
        next.addClickListener(e -> { page++; load(); });
        feedback.getElement().setAttribute("role", "status");
        results.setWidthFull();
        add(new H1("Moderate listings"), new Anchor("admin/reports", "Review reports"),
                new Paragraph("Review expired, sold, or withdrawn listings. Removal hides a listing from the marketplace and prevents the owner from renewing it. Listing history is retained."),
                filter, new Button("Refresh", e -> load()), feedback, results, new Div(previous, next));
    }
    @Override public void beforeEnter(BeforeEnterEvent event) { page = 0; load(); }

    private void load() {
        results.removeAll(); previous.setEnabled(false); next.setEnabled(false);
        feedback.setText("");
        try {
            var data = service.moderationQueue(filter.getValue(), page);
            if (data.isEmpty() && page > 0) { page--; load(); return; }
            previous.setEnabled(data.hasPrevious()); next.setEnabled(data.hasNext());
            if (data.isEmpty()) results.add(new Paragraph("No listings match this filter."));
            var format = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z")
                    .withZone(com.mavworks.service.BookingService.BOOKING_ZONE);
            for (var listing : data) {
                Div card = new Div();
                card.addClassName("mw-panel");
                card.getStyle().set("padding", "1rem").set("margin-bottom", "1rem");
                String status = listing.status() == ListingStatus.ACTIVE && !listing.expires().isAfter(java.time.Instant.now())
                        ? "Expired" : listing.status().name();
                card.add(new H2(listing.name()), new Paragraph("Listing #" + listing.id() + " · Owner: " + listing.owner()),
                        new Paragraph("Status: " + status),
                        new Paragraph("Posted: " + format.format(listing.posted()) + " · Expires: " + format.format(listing.expires())));
                if (listing.status() != ListingStatus.REMOVED)
                    card.add(new Button("Remove listing", e -> confirmRemoval(listing)));
                results.add(card);
            }
        } catch (DataAccessException ex) { feedback.setText("Listings could not be loaded. Select Refresh to retry."); }
        catch (AccessDeniedException ex) { feedback.setText(ex.getMessage()); }
    }

    private void confirmRemoval(ListingManagementService.ModerationListing listing) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Remove listing?");
        dialog.add(new Paragraph("Remove “" + listing.name() + "” (#" + listing.id() + ") by " + listing.owner()
                + "? It will be hidden from the marketplace and its owner will no longer be able to edit or renew it."));
        Button remove = new Button("Confirm removal");
        remove.setDisableOnClick(true);
        remove.addClickListener(e -> {
            try {
                service.remove(listing.id()); dialog.close(); load();
                feedback.setText("Listing #" + listing.id() + " removed.");
            } catch (IllegalArgumentException | AccessDeniedException ex) { feedback.setText(ex.getMessage()); dialog.close(); }
            catch (DataAccessException ex) {
                dialog.add(new Paragraph("The listing could not be removed. Please retry."));
            } finally { remove.setEnabled(true); }
        });
        dialog.add(new Button("Cancel", e -> dialog.close()), remove);
        add(dialog);
        dialog.addOpenedChangeListener(e -> { if (!e.isOpened()) remove(dialog); });
        dialog.open();
    }
}

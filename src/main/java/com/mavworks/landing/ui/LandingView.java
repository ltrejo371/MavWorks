package com.mavworks.landing.ui;

import com.mavworks.base.ui.ViewTitle;
import com.mavworks.dto.ListingSummary;
import com.mavworks.dto.SearchFilter;
import com.mavworks.model.ListingCategory;
import com.mavworks.model.ListingType;
import com.mavworks.security.CurrentUser;
import com.mavworks.service.ListingCatalogService;
import com.mavworks.service.LocalImageStorageService;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.text.NumberFormat;
import java.util.Locale;
import org.springframework.dao.DataAccessException;

@Route("")
@RouteAlias("market")
@PageTitle("Home")
@Menu(order = 0, icon = "icons/clipboard-check.svg", title = "Home")
@PermitAll
@CssImport("./styles/marketplace.css")
public class LandingView extends VerticalLayout {
    private final TextField search = new TextField("Search listings");
    private final ComboBox<ListingCategory> category = new ComboBox<>("Category");
    private final ComboBox<ListingType> type = new ComboBox<>("Listing type");
    private final Paragraph status = new Paragraph();
    private final Div results = new Div();
    private final ListingCatalogService catalog;
    private final LocalImageStorageService images;

    public LandingView(CurrentUser currentUser, ListingCatalogService catalog, LocalImageStorageService images) {
        this.catalog = catalog;
        this.images = images;
        add(new ViewTitle("Home"),
                new Paragraph("Welcome, " + currentUser.username().orElse("there")
                        + "! Browse products and services from the MavWorks community."));
        search.setPlaceholder("Search by name or description");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setWidth("20rem");
        category.setItems(ListingCategory.values());
        category.setItemLabelGenerator(value -> label(value.name()));
        category.setPlaceholder("All categories");
        category.setClearButtonVisible(true);
        type.setItems(ListingType.values());
        type.setItemLabelGenerator(value -> label(value.name()));
        type.setPlaceholder("Products and services");
        type.setClearButtonVisible(true);
        Button refresh = new Button("Refresh", event -> loadListings());
        HorizontalLayout filters = new HorizontalLayout(search, category, type, refresh);
        filters.setAlignItems(Alignment.END);
        filters.setWidthFull();
        filters.getStyle().set("flex-wrap", "wrap");
        results.setWidthFull();
        results.addClassName("marketplace-results");
        status.getElement().setAttribute("role", "status");
        add(filters, status, results);
        search.addValueChangeListener(event -> loadListings());
        category.addValueChangeListener(event -> loadListings());
        type.addValueChangeListener(event -> loadListings());
        loadListings();
    }

    private void loadListings() {
        results.removeAll();
        try {
            var listings = catalog.search(new SearchFilter(search.getValue(), category.getValue()),
                    type.getValue());
            boolean filtered = !search.getValue().isBlank() || category.getValue() != null
                    || type.getValue() != null;
            status.setText(listings.isEmpty()
                    ? (filtered ? "No listings match your filters. Try another search."
                                : "No listings have been published yet.")
                    : listings.size() + (listings.size() == 1 ? " listing" : " listings"));
            listings.forEach(listing -> results.add(card(listing)));
        } catch (DataAccessException ex) {
            status.setText("Listings could not be loaded. Select Refresh to try again.");
        }
    }

    private Div card(ListingSummary listing) {
        Div card = new Div();
        card.addClassName("marketplace-card");
        String description = listing.description();
        if (description.length() > 180) {
            description = description.substring(0, 177) + "...";
        }
        Div details = new Div();
        details.addClassName("marketplace-details");
        H2 title = new H2();
        title.add(new Anchor("listings/" + listing.id(), listing.name()));
        Paragraph price = new Paragraph(NumberFormat.getCurrencyInstance(Locale.US).format(listing.price()));
        price.addClassName("marketplace-price");
        Paragraph summary = new Paragraph(description);
        summary.addClassName("marketplace-description");
        Paragraph category = new Paragraph(label(listing.type().name()) + " · " + label(listing.category().name()));
        category.addClassName("marketplace-category");
        details.add(title, price, summary, category);
        card.add(preview(listing), details);
        return card;
    }

    private Anchor preview(ListingSummary listing) {
        Anchor preview = new Anchor("listings/" + listing.id(), "");
        preview.addClassName("marketplace-preview");
        preview.getElement().setAttribute("aria-label", "View " + listing.name());
        preview.getStyle().set("display", "grid").set("place-items", "center")
                .set("position", "relative").set("width", "100%").set("aspect-ratio", "1 / 1")
                .set("overflow", "hidden").set("border-radius", "0.5rem")
                .set("background", "#f2f3f5").set("color", "#6e6e73")
                .set("text-decoration", "none");
        preview.add(new Span("No image available"));
        String reference = listing.imageReference();
        if (reference != null && !reference.isBlank()) {
            var handler = DownloadHandler.fromInputStream(event -> {
                try {
                    return new DownloadResponse(images.open(reference), reference,
                            reference.endsWith("png") ? "image/png" : "image/jpeg", -1);
                } catch (java.io.IOException | IllegalArgumentException ex) {
                    return DownloadResponse.error(404);
                }
            });
            Image image = new Image(handler, listing.name());
            image.getElement().setAttribute("loading", "lazy");
            image.getElement().setAttribute("decoding", "async");
            image.getStyle().set("position", "absolute").set("inset", "0")
                    .set("width", "100%").set("height", "100%")
                    .set("object-fit", "cover").set("background", "#f2f3f5");
            image.getElement().addEventListener("error", event -> image.setVisible(false));
            preview.add(image);
        }
        return preview;
    }

    private static String label(String value) {
        String text = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}

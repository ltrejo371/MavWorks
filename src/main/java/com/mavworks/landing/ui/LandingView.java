package com.mavworks.landing.ui;

import com.mavworks.base.ui.ViewTitle;
import com.mavworks.dto.ListingSummary;
import com.mavworks.dto.SearchFilter;
import com.mavworks.model.ListingCategory;
import com.mavworks.model.ListingType;
import com.mavworks.security.CurrentUser;
import com.mavworks.service.ListingCatalogService;
import com.mavworks.service.ImageStorageService;
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
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.time.LocalDate;
import java.text.NumberFormat;
import java.util.Locale;
import org.springframework.dao.DataAccessException;

@Route("")
@RouteAlias("market")
@PageTitle("Home")
@Menu(order = 0, icon = "vaadin:home", title = "Home")
@PermitAll
@CssImport("./styles/marketplace.css")
public class LandingView extends VerticalLayout {
    private final TextField search = new TextField("Search listings");
    private final ComboBox<ListingCategory> category = new ComboBox<>("Category");
    private final ComboBox<ListingType> type = new ComboBox<>("Listing type");
    private final ComboBox<ListingCatalogService.SortOption> sort = new ComboBox<>("Sort by");
    private final ComboBox<ListingCatalogService.DateRange> dateRange = new ComboBox<>("Uploaded");
    private final DatePicker fromDate = new DatePicker("From");
    private final DatePicker throughDate = new DatePicker("Through");
    private final Paragraph status = new Paragraph();
    private final Div results = new Div();
    private final Button previous = new Button("Previous");
    private final Button next = new Button("Next");
    private final ListingCatalogService catalog;
    private final ImageStorageService images;
    private int page;

    public LandingView(CurrentUser currentUser, ListingCatalogService catalog, ImageStorageService images) {
        this.catalog = catalog;
        this.images = images;
        addClassNames("mw-page", "marketplace-page");
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
        sort.setItems(ListingCatalogService.SortOption.values());
        sort.setItemLabelGenerator(value -> switch (value) {
            case NEWEST -> "Newest";
            case PRICE_LOW_TO_HIGH -> "Price: low to high";
            case PRICE_HIGH_TO_LOW -> "Price: high to low";
            case MOST_VIEWED -> "Most viewed";
        });
        sort.setValue(ListingCatalogService.SortOption.NEWEST);
        dateRange.setItems(ListingCatalogService.DateRange.values());
        dateRange.setItemLabelGenerator(value -> switch (value) {
            case ANY_TIME -> "Any time";
            case TODAY -> "Today";
            case LAST_7_DAYS -> "Last 7 days";
            case LAST_30_DAYS -> "Last 30 days";
            case LAST_365_DAYS -> "Last year";
            case CUSTOM -> "Custom dates";
        });
        dateRange.setValue(ListingCatalogService.DateRange.ANY_TIME);
        fromDate.setVisible(false);
        throughDate.setVisible(false);
        dateRange.addValueChangeListener(event -> {
            boolean custom = event.getValue() == ListingCatalogService.DateRange.CUSTOM;
            fromDate.setVisible(custom);
            throughDate.setVisible(custom);
            filtersChanged();
        });
        Button refresh = new Button("Refresh", event -> loadListings());
        HorizontalLayout filters = new HorizontalLayout(search, category, type, sort, dateRange,
                fromDate, throughDate, refresh);
        filters.setAlignItems(Alignment.END);
        filters.setWidthFull();
        filters.getStyle().set("flex-wrap", "wrap");
        results.setWidthFull();
        results.addClassName("marketplace-results");
        status.getElement().setAttribute("role", "status");
        previous.addClickListener(event -> {
            if (page > 0) {
                page--;
                loadListings();
            }
        });
        next.addClickListener(event -> {
            page++;
            loadListings();
        });
        previous.setEnabled(false);
        next.setEnabled(false);
        add(filters, status, results, new HorizontalLayout(previous, next));
        search.addValueChangeListener(event -> filtersChanged());
        category.addValueChangeListener(event -> filtersChanged());
        type.addValueChangeListener(event -> filtersChanged());
        sort.addValueChangeListener(event -> filtersChanged());
        fromDate.addValueChangeListener(event -> filtersChanged());
        throughDate.addValueChangeListener(event -> filtersChanged());
        loadListings();
    }

    private void filtersChanged() {
        page = 0;
        loadListings();
    }

    private void loadListings() {
        results.removeAll();
        try {
            var listings = catalog.search(new SearchFilter(search.getValue(), category.getValue()),
                    type.getValue(), sort.getValue(), dateRange.getValue(), fromDate.getValue(),
                    throughDate.getValue(), page);
            boolean filtered = !search.getValue().isBlank() || category.getValue() != null
                    || type.getValue() != null
                    || dateRange.getValue() != ListingCatalogService.DateRange.ANY_TIME;
            status.setText(listings.getTotalElements() == 0
                    ? (filtered ? "No listings match your filters. Try another search."
                                : "No listings have been published yet.")
                    : "Showing " + (listings.getNumber() * listings.getSize() + 1) + "–"
                            + (listings.getNumber() * listings.getSize() + listings.getNumberOfElements())
                            + " of " + listings.getTotalElements() + " listings · Page "
                            + (listings.getNumber() + 1) + " of " + listings.getTotalPages());
            listings.getContent().forEach(listing -> results.add(card(listing)));
            previous.setEnabled(listings.hasPrevious());
            next.setEnabled(listings.hasNext());
        } catch (IllegalArgumentException ex) {
            status.setText(ex.getMessage());
            previous.setEnabled(false);
            next.setEnabled(false);
        } catch (DataAccessException ex) {
            status.setText("Listings could not be loaded. Select Refresh to try again.");
            previous.setEnabled(false);
            next.setEnabled(false);
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
        Paragraph views = new Paragraph(listing.viewCount() + " views");
        views.addClassName("marketplace-views");
        details.add(title, price, summary, category, views);
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

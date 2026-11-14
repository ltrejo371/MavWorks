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
import java.time.Instant;
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
    private final ComboBox<com.mavworks.model.ListingSort> sort = new ComboBox<>("Sort by");
    private final ComboBox<String> posted = new ComboBox<>("Posted");
    private final DatePicker from = new DatePicker("From date");
    private final DatePicker to = new DatePicker("To date");
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
        sort.setItems(com.mavworks.model.ListingSort.values());
        sort.setItemLabelGenerator(com.mavworks.model.ListingSort::label);
        sort.setValue(com.mavworks.model.ListingSort.NEWEST);
        posted.setItems("All time", "Today", "Past week", "Past month", "Past year", "Custom range");
        posted.setValue("All time");
        from.setVisible(false);
        to.setVisible(false);
        Button refresh = new Button("Refresh", event -> loadListings());
        HorizontalLayout filters = new HorizontalLayout(search, sort, category, type, posted, from, to, refresh);
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
        posted.addValueChangeListener(event -> {
            boolean custom = "Custom range".equals(posted.getValue());
            from.setVisible(custom); to.setVisible(custom);
            filtersChanged();
        });
        from.addValueChangeListener(event -> filtersChanged());
        to.addValueChangeListener(event -> filtersChanged());
        loadListings();
    }

    private void filtersChanged() {
        page = 0;
        loadListings();
    }

    private void loadListings() {
        results.removeAll();
        try {
            var listings = catalog.search(searchFilter(), type.getValue(), page);
            boolean filtered = !search.getValue().isBlank() || category.getValue() != null
                    || type.getValue() != null
                    || !"All time".equals(posted.getValue());
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

    private SearchFilter searchFilter() {
        // Marketplace dates follow the campus time zone, including daylight-saving changes.
        var zone = java.time.ZoneId.of("America/Chicago");
        var today = LocalDate.now(zone);
        String range = posted.getValue() == null ? "All time" : posted.getValue();
        LocalDate start = switch (range) {
            case "Today" -> today;
            case "Past week" -> today.minusWeeks(1);
            case "Past month" -> today.minusMonths(1);
            case "Past year" -> today.minusYears(1);
            case "Custom range" -> from.getValue();
            default -> null;
        };
        LocalDate end = "Custom range".equals(range) ? to.getValue() : today;
        if ("Custom range".equals(range) && (start == null || end == null || from.isInvalid() || to.isInvalid())) {
            throw new IllegalArgumentException("Choose valid From and To dates for the custom range.");
        }
        Instant startInstant = start == null ? null : start.atStartOfDay(zone).toInstant();
        Instant endInstant = start == null ? null : end.plusDays(1).atStartOfDay(zone).toInstant();
        return new SearchFilter(search.getValue(), category.getValue(), sort.getValue(),
                startInstant, endInstant);
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
        Paragraph views = new Paragraph(com.mavworks.base.ui.ListingPreview.views(listing.viewCount()));
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

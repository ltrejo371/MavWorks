package com.mavworks.landing.ui;

import com.mavworks.base.ui.ViewTitle;
import com.mavworks.dto.ListingSummary;
import com.mavworks.dto.SearchFilter;
import com.mavworks.model.ListingCategory;
import com.mavworks.model.ListingType;
import com.mavworks.security.CurrentUser;
import com.mavworks.service.ListingCatalogService;
import com.vaadin.flow.component.button.Button;
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
public class LandingView extends VerticalLayout {
    private final TextField search = new TextField("Search listings");
    private final ComboBox<ListingCategory> category = new ComboBox<>("Category");
    private final ComboBox<ListingType> type = new ComboBox<>("Listing type");
    private final Paragraph status = new Paragraph();
    private final Div results = new Div();
    private final ListingCatalogService catalog;

    public LandingView(CurrentUser currentUser, ListingCatalogService catalog) {
        this.catalog = catalog;
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
        results.getStyle().set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fit, minmax(min(100%, 18rem), 1fr))")
                .set("gap", "1rem");
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
        card.getStyle().set("padding", "1rem").set("border", "1px solid #d8dce2")
                .set("border-radius", "0.75rem");
        String description = listing.description();
        if (description.length() > 180) {
            description = description.substring(0, 177) + "...";
        }
        card.add(new H2(listing.name()),
                new Paragraph(NumberFormat.getCurrencyInstance(Locale.US).format(listing.price())),
                new Paragraph(description),
                new Paragraph(label(listing.type().name()) + " · " + label(listing.category().name())));
        return card;
    }

    private static String label(String value) {
        String text = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}

package com.mavworks.dashboard;

import com.mavworks.base.ui.ViewTitle;
import com.mavworks.service.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.*;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.text.NumberFormat;
import java.util.Locale;
import org.springframework.dao.DataAccessException;

@Route("dashboard")
@PageTitle("Dashboard")
@Menu(order = 1, icon = "icons/clipboard-check.svg", title = "Dashboard")
@PermitAll
@CssImport("./styles/dashboard.css")
public class DashboardView extends VerticalLayout {
    private final Div account = new Div();
    private final AccountEditingService editing;
    private final LocalImageStorageService storage;

    public DashboardView(AccountDashboardService service, AccountEditingService editing, LocalImageStorageService storage) {
        this.editing = editing; this.storage = storage;
        addClassName("dashboard"); setPadding(false);
        Div heading = new Div(new ViewTitle("Dashboard"), new Paragraph("Manage your profile and marketplace activity."));
        Button addListing = new Button("Add listing", e -> getUI().ifPresent(ui -> ui.navigate(com.mavworks.listing.CreateListingView.class)));
        addListing.addThemeVariants(ButtonVariant.PRIMARY);
        Div actions = new Div(new Button("Refresh", e -> loadAccount(service)), addListing);
        actions.addClassName("dashboard-actions");
        Div header = new Div(heading, actions); header.addClassName("dashboard-header");
        account.addClassName("dashboard-content"); add(header, account);
        loadAccount(service);
    }

    private void loadAccount(AccountDashboardService service) {
        account.removeAll();
        try {
            var data = service.load();
            Div profile = card("Your profile");
            H3 profileTitle = (H3) profile.getComponentAt(0);
            profileTitle.removeAll();
            Anchor profileLink = new Anchor("profiles/" + data.userId(), "Your profile →");
            profileLink.getElement().setAttribute("aria-label", "View your public profile");
            profileTitle.add(profileLink);
            Div identity = new Div(); identity.addClassName("dashboard-identity");
            if (data.hasPicture()) identity.add(EditAccountDialog.picture(data.userId()));
            else {
                Span initial = new Span(data.username().substring(0, 1).toUpperCase(Locale.ROOT));
                initial.addClassName("dashboard-avatar"); identity.add(initial);
            }
            Div names = new Div(new H2(data.username()), new Paragraph(data.email()));
            names.addClassName("dashboard-names"); identity.add(names);
            Button edit = new Button("Edit profile", e -> new EditAccountDialog(data, editing, storage, () -> loadAccount(service)).open());
            Div profileHeader = new Div(identity, edit); profileHeader.addClassName("dashboard-profile-header");
            Div details = new Div(field("Name", data.name()), field("Contact information", data.contactInformation()));
            details.addClassName("dashboard-details");
            profile.add(profileHeader, field("Bio", data.bio()), details); account.add(profile);
            if (!data.warnings().isEmpty()) {
                Div warnings = card("Account warnings"); warnings.addClassName("dashboard-warnings");
                data.warnings().forEach(w -> warnings.add(new Paragraph(w.category().getLabel() + " — " + w.explanation())));
                account.add(warnings);
            }
            Div grid = new Div(); grid.addClassName("dashboard-grid");
            Div listings = card("Your listings", data.listings().size());
            if (data.listings().isEmpty()) listings.add(empty("Your listings will appear here once you add one."));
            data.listings().forEach(listing -> {
                Anchor link = new Anchor("listings/" + listing.id(), listing.name());
                Span price = new Span(NumberFormat.getCurrencyInstance(Locale.US).format(listing.price()));
                price.addClassName("dashboard-price"); listings.add(row(link, price));
            });
            Div reports = card("Your reports", data.submittedReports().size());
            if (data.submittedReports().isEmpty()) reports.add(empty("You haven't submitted any reports."));
            data.submittedReports().forEach(report -> {
                Div text = new Div(new Span(report.reportedUser()), new Paragraph("#" + report.id() + " · " + report.category().getLabel()));
                Span status = new Span(switch (report.status()) {
                    case PENDING -> "Pending"; case VALID -> "Valid"; case INVALID -> "Invalid";
                });
                status.addClassNames("dashboard-status", "dashboard-status-" + report.status().name().toLowerCase(Locale.ROOT));
                reports.add(row(text, status));
            });
            Div blocked = card("Blocked accounts", data.blockedAccounts().size());
            if (data.blockedAccounts().isEmpty()) blocked.add(empty("You haven't blocked any accounts."));
            data.blockedAccounts().forEach(name -> blocked.add(row(new Span(name), new Span("Blocked"))));
            grid.add(listings, reports, blocked); account.add(grid);
        } catch (DataAccessException ex) {
            Paragraph error = new Paragraph("Your account could not be loaded. Select Refresh to try again.");
            error.getElement().setAttribute("role", "alert"); account.add(error);
        }
    }
    private static Div card(String title) {
        Div card = new Div(new H3(title)); card.addClassName("dashboard-card"); return card;
    }
    private static Div card(String title, int count) {
        Div card = card(title);
        Span badge = new Span(Integer.toString(count)); badge.addClassName("dashboard-count");
        ((H3) card.getComponentAt(0)).add(badge); return card;
    }
    private static Div row(Component left, Component right) {
        Div row = new Div(left, right); row.addClassName("dashboard-row"); return row;
    }
    private static Paragraph empty(String text) {
        Paragraph empty = new Paragraph(text); empty.addClassName("dashboard-empty"); return empty;
    }
    private static Div field(String label, String value) {
        Span caption = new Span(label); caption.addClassName("dashboard-label");
        Div field = new Div(caption, new Paragraph(value == null || value.isBlank() ? "Not provided" : value));
        field.addClassName("dashboard-field"); return field;
    }
}

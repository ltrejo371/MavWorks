package com.mavworks.dashboard;

import com.mavworks.base.ui.ViewTitle;
import com.mavworks.service.AccountDashboardService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
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
public class DashboardView extends VerticalLayout {
    private final VerticalLayout account = new VerticalLayout();

    public DashboardView(AccountDashboardService service) {
        account.setPadding(false);
        add(new ViewTitle("Dashboard"), new Paragraph("Your account and your listings."),
                account, new Button("Refresh", event -> loadAccount(service)));
        loadAccount(service);
    }

    private void loadAccount(AccountDashboardService service) {
        account.removeAll();
        try {
            var data = service.load();
            account.add(new H2(data.username()),
                    new Paragraph("Email: " + data.email()),
                    new Paragraph("Name: " + display(data.name())),
                    new Paragraph("Bio: " + display(data.bio())),
                    new Paragraph("Contact information: " + display(data.contactInformation())),
                    new H2("Your listings"));
            if (data.listings().isEmpty()) {
                account.add(new Paragraph("You have not created any listings yet."));
            } else {
                data.listings().forEach(listing -> account.add(new Paragraph(listing.name() + " — "
                        + NumberFormat.getCurrencyInstance(Locale.US).format(listing.price()))));
            }
        } catch (DataAccessException ex) {
            account.add(new Paragraph("Your account could not be loaded. Select Refresh to try again."));
        }
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "Not provided" : value;
    }
}

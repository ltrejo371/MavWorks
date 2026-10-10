package com.mavworks.listing;

import com.mavworks.base.ui.ViewTitle;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route("listings/new")
@PageTitle("Create Listing")
@PermitAll
public class CreateListingView extends VerticalLayout {
    public CreateListingView() {
        add(new ViewTitle("Create Listing"),
                new Paragraph("This screen is a placeholder. Its form and service integration are not implemented yet."));
    }
}

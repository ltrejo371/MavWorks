package com.mavworks.dashboard;
import com.mavworks.assets.ListingPersonalView;
import com.mavworks.base.ui.ViewTitle;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.virtuallist.VirtualList;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

@Route(value = "dashboard")
@PageTitle("Dashboard")
@Menu(order = 1, icon = "icons/clipboard-check.svg", title = "Dashboard")
@PermitAll

public class DashboardView extends VerticalLayout{
    private VirtualList<ListingPersonalView> listingsList = new VirtualList<ListingPersonalView>();
    public DashboardView(){
        var header = new HorizontalLayout();
        header.add(new ViewTitle("Welcome"));

        listingsList.setItems(new ListingPersonalView("Placeholder", "Placeholder", "This is a placeholder"), 
            new ListingPersonalView("Placeholder", "Placeholder", "This is a placeholder"), 
            new ListingPersonalView("Placeholder", "Placeholder", "This is a placeholder"));

        this.setSizeFull();
        this.add(header);
        this.add(listingsList);
    }
}

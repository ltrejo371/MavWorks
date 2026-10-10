package com.mavworks.assets;

import com.mavworks.data.Account;
import com.mavworks.data.Listing;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class ListingView extends VerticalLayout{
    private final Header header = new Header();
    private final Div description = new Div();
    private final Button viewBtn;
    private final Account account;

    public ListingView(Listing listing, Account account) {
        super();
        this.account = account;

        this.header.setTitle(listing.getName());
        this.header.setText(this.account.getUsername());

        this.description.setText(listing.getDescription());
        this.viewBtn = new Button("View", event -> gotoAccount(this.account));

        add(header);
        add(description);
        add(viewBtn);
    }

    public ListingView(String name, String username, String description) { //Placeholder constructor
        super();
        this.account = null;

        this.header.setTitle(name);
        this.header.setText(username);

        this.description.setText(description);
        this.viewBtn = new Button("View", event -> gotoAccount(this.account));

        add(header);
        add(this.description);
        add(viewBtn);
    }

    private void gotoAccount(Account account){
        //TODO: Implemenet Account Routing
    }
}

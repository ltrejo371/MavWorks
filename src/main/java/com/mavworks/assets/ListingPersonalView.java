package com.mavworks.assets;

import com.mavworks.data.Account;
import com.mavworks.data.Listing;
import com.vaadin.flow.component.button.Button;

public class ListingPersonalView extends ListingView{
    private Button editBtn;

    public ListingPersonalView(Listing listing, Account account){
        super(listing, account);
        this.editBtn = new Button("Edit", event -> editListing());
        add(editBtn);
    }

    public ListingPersonalView(String name, String username, String description){
        super(name, username, description);
        this.editBtn = new Button("Edit", event -> editListing());
        add(editBtn);
    }

    public void editListing(){
        // TODO: Implement Listing Editing
    }
}

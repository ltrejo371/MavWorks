package com.mavworks.data;

import java.util.ArrayList;

public class Account {
    private String username;
    private ArrayList<Listing> listings = new ArrayList<Listing>();

    public Account(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void addListing(Listing listing) {
        this.listings.add(listing);
    }

    public ArrayList<Listing> getListings() {
        return this.listings;
    }
}

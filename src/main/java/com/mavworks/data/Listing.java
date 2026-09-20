package com.mavworks.data;

public class Listing {
    private String name;
    private String description;
    private Account associatedAccount;

    public Listing(String name, String description, Account associatedAccount){
        this.name = name;
        this.description = description;
        this.associatedAccount = associatedAccount;
    }

    public String getName(){
        return name;
    }

    public String getDescription(){
        return description;
    }

    public Account getAssociatedAccount(){
        return associatedAccount;
    }

    public void setName(String name){
        this.name = name;
    }
    
    public void setDescription(String description){
        this.description = description;
    }
}

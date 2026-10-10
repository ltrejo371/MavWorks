package com.mavworks.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Profile data owned and persisted by User. */
@Embeddable
public class Profile {
    private String name;
    @Column(length = 2000)
    private String bio;
    private String profilePictureUrl;
    private String contactInformation;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getProfilePictureUrl() { return profilePictureUrl; }
    public void setProfilePictureUrl(String url) { this.profilePictureUrl = url; }
    public String getContactInformation() { return contactInformation; }
    public void setContactInformation(String info) { this.contactInformation = info; }
}

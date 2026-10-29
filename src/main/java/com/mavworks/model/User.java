package com.mavworks.model;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class User implements UserActions {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String hashedPassword;

    @Embedded
    private Profile profile = new Profile();

    public User() {
    }

    @Override
    public void createListing() {
        // TODO: Implement.
    }

    @Override
    public void deleteListing() {
        // TODO: Implement.
    }

    @Override
    public void sendMessage() {
        // TODO: Implement.
    }

    @Override
    public void leaveReview() {
        // TODO: Implement.
    }

    @Override
    public void blockUser() {
        // TODO: Implement.
    }

    @Override
    public void reportUser() {
        // TODO: Implement.
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getHashedPassword() {
        return hashedPassword;
    }

    public void setHashedPassword(String hashedPassword) {
        this.hashedPassword = hashedPassword;
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }
}

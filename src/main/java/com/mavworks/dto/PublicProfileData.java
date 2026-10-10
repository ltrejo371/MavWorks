package com.mavworks.dto;

/** Explicit public projection: never expose an account entity to a public view. */
public record PublicProfileData(Long userId, String username, String name, String bio,
        String profilePictureUrl, String contactInformation) {
}

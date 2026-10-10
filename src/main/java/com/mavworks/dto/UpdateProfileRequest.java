package com.mavworks.dto;

public record UpdateProfileRequest(String name, String bio,
        String profilePictureUrl, String contactInformation) {
}

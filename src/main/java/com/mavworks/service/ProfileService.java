package com.mavworks.service;

import com.mavworks.dto.PublicProfileData;
import com.mavworks.dto.UpdateProfileRequest;
import java.util.Optional;

/** Implementation contract only; not yet wired into the views. */
public interface ProfileService {
    // Resolve the user from authentication and enforce ownership in implementation.
    void updateMyProfile(UpdateProfileRequest request);
    Optional<PublicProfileData> getPublicProfile(Long userId);
}

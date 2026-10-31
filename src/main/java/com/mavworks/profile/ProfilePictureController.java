package com.mavworks.profile;

import com.mavworks.service.LocalImageStorageService;
import com.mavworks.service.ReviewService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Rechecks profile visibility for every image request, including after a block. */
@RestController
public class ProfilePictureController {
    private final ReviewService profiles;
    private final LocalImageStorageService images;
    public ProfilePictureController(ReviewService profiles, LocalImageStorageService images) {
        this.profiles = profiles; this.images = images;
    }
    @GetMapping("/profile-pictures/{id}")
    public ResponseEntity<byte[]> picture(@PathVariable Long id) throws java.io.IOException {
        var profile = profiles.getProviderProfile(id);
        if (profile.isEmpty() || profile.get().profile().profilePictureUrl() == null)
            return ResponseEntity.notFound().build();
        String reference = profile.get().profile().profilePictureUrl();
        try (var stream = images.open(reference)) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .contentType(reference.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                    .body(stream.readAllBytes());
        } catch (java.nio.file.NoSuchFileException | IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}

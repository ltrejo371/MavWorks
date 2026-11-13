package com.mavworks.service;

import com.mavworks.model.Profile;
import com.mavworks.repository.UserRepository;
import com.mavworks.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@Service
public class AccountEditingService {
    private static final java.time.Duration USERNAME_CHANGE_COOLDOWN = java.time.Duration.ofDays(30);
    private final CurrentUser current;
    private final UserRepository users;
    private final ImageStorageService images;
    public AccountEditingService(CurrentUser current, UserRepository users, ImageStorageService images) {
        this.current = current; this.users = users; this.images = images;
    }
    /** Null picture retains the current picture. Identity always comes from the session. */
    @Transactional
    public void save(String username, String bio, String pictureReference) {
        save(username, bio, pictureReference, null);
    }
    /** Null contact retains it; an empty value clears it. */
    @Transactional
    public void save(String username, String bio, String pictureReference, String contactInformation) {
        var user = current.username().flatMap(users::findForBlock)
                .orElseThrow(() -> new AccessDeniedException("Sign in to edit your account."));
        String name = username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
        if (!name.matches("[a-z0-9_.-]{3,30}"))
            throw new IllegalArgumentException("Username must contain 3–30 letters, numbers, dots, underscores, or hyphens.");
        String text = bio == null ? "" : bio.trim();
        if (text.length() > 2000) throw new IllegalArgumentException("Bio must be at most 2000 characters.");
        String contact = contactInformation == null ? null : contactInformation.trim();
        if (contact != null && contact.length() > 255)
            throw new IllegalArgumentException("Contact information must be at most 255 characters.");
        boolean usernameChanged = !name.equals(user.getUsername());
        java.time.Instant now = java.time.Instant.now();
        if (usernameChanged && user.getUsernameLastChangedAt() != null
                && now.isBefore(user.getUsernameLastChangedAt().plus(USERNAME_CHANGE_COOLDOWN))) {
            throw new IllegalArgumentException("You can change your username once every 30 days. Please try again later.");
        }
        if (usernameChanged && users.existsByUsername(name))
            throw new IllegalArgumentException("This username is unavailable. Choose another username.");
        if (pictureReference != null) images.validateOwnedReference(pictureReference);
        if (user.getProfile() == null) user.setProfile(new Profile());
        user.getProfile().setBio(text);
        if (contact != null) user.getProfile().setContactInformation(contact);
        if (pictureReference != null) user.getProfile().setProfilePictureUrl(pictureReference);
        user.setUsername(name);
        if (usernameChanged) user.recordUsernameChange(now);
        users.flush();
    }
}

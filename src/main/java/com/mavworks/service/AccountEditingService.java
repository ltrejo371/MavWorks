package com.mavworks.service;

import com.mavworks.model.Profile;
import com.mavworks.repository.UserRepository;
import com.mavworks.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@Service
public class AccountEditingService {
    private final CurrentUser current;
    private final UserRepository users;
    private final LocalImageStorageService images;
    public AccountEditingService(CurrentUser current, UserRepository users, LocalImageStorageService images) {
        this.current = current; this.users = users; this.images = images;
    }
    /** Null picture retains the current picture. Identity always comes from the session. */
    @Transactional
    public void save(String username, String bio, String pictureReference) {
        var user = current.username().flatMap(users::findForBlock)
                .orElseThrow(() -> new AccessDeniedException("Sign in to edit your account."));
        String name = username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
        if (!name.matches("[a-z0-9_.-]{3,30}"))
            throw new IllegalArgumentException("Username must contain 3–30 letters, numbers, dots, underscores, or hyphens.");
        String text = bio == null ? "" : bio.trim();
        if (text.length() > 2000) throw new IllegalArgumentException("Bio must be at most 2000 characters.");
        if (!name.equals(user.getUsername()) && users.existsByUsername(name))
            throw new IllegalArgumentException("This username is unavailable. Choose another username.");
        if (pictureReference != null) images.validateOwnedReference(pictureReference);
        if (user.getProfile() == null) user.setProfile(new Profile());
        user.getProfile().setBio(text);
        if (pictureReference != null) user.getProfile().setProfilePictureUrl(pictureReference);
        user.setUsername(name);
        users.flush();
    }
}

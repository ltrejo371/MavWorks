package com.mavworks.service;

import com.mavworks.repository.UserRepository;
import com.mavworks.security.CurrentUser;
import com.mavworks.service.ImageValidationService;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

/** Files use server-generated names; original filenames are never used as paths. */
@Service
@Profile("!supabase")
public class LocalImageStorageService implements ImageStorageService {
    private final Path root;
    private final CurrentUser currentUser;
    private final UserRepository users;

    public LocalImageStorageService(@Value("${mavworks.images-directory:data/listing-images}") String directory,
            CurrentUser currentUser, UserRepository users) {
        root = Path.of(directory).toAbsolutePath().normalize();
        this.currentUser = currentUser;
        this.users = users;
    }

    private String ownerPrefix() {
        return currentUser.username().flatMap(users::findByUsername)
                .map(user -> user.getId() + "-")
                .orElseThrow(() -> new AccessDeniedException("Sign in to upload images."));
    }

    @Override
    public String store(InputStream content, String originalFilename, String contentType) {
        String prefix = ownerPrefix();
        try {
            byte[] bytes = content.readNBytes(ImageValidationService.MAX_BYTES + 1);
            String extension = ImageValidationService.validate(bytes);
            String reference = prefix + UUID.randomUUID() + "." + extension;
            Files.createDirectories(root);
            Files.write(path(reference), bytes, StandardOpenOption.CREATE_NEW);
            return reference;
        } catch (IOException ex) {
            throw new UncheckedIOException("Image could not be saved. Try again.", ex);
        }
    }

    private Path path(String reference) {
        if (reference == null || !reference.matches("[0-9]+-[0-9a-f-]{36}\\.(jpg|png)")) {
            throw new IllegalArgumentException("Invalid image reference.");
        }
        return root.resolve(reference);
    }

    @Override
    public void validateOwnedReference(String reference) {
        Path file = path(reference);
        if (!reference.startsWith(ownerPrefix()) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("An image is unavailable. Select it again.");
        }
    }

    @Override
    public InputStream open(String reference) throws IOException {
        ownerPrefix();
        return Files.newInputStream(path(reference));
    }

    @Override
    public void delete(String reference) {
        validateOwnedReference(reference);
        try {
            Files.deleteIfExists(path(reference));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}

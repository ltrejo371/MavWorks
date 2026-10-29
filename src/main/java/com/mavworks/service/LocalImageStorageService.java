package com.mavworks.service;

import com.mavworks.repository.UserRepository;
import com.mavworks.security.CurrentUser;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Files use server-generated names; original filenames are never used as paths. */
@Service
public class LocalImageStorageService implements ImageStorageService {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
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

    public static String validate(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Each image must be nonempty and at most 5 MB.");
        }
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Select a supported JPEG or PNG image.");
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg")) {
                    throw new IllegalArgumentException("Select a supported JPEG or PNG image.");
                }
                reader.setInput(input);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000) {
                    throw new IllegalArgumentException("Images must be at most 20 megapixels.");
                }
                reader.read(0);
                return format.equals("jpeg") ? "jpg" : "png";
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("The image cannot be read. Select another JPEG or PNG.", ex);
        }
    }

    @Override
    public String store(InputStream content, String originalFilename, String contentType) {
        String prefix = ownerPrefix();
        try {
            byte[] bytes = content.readNBytes(MAX_BYTES + 1);
            String extension = validate(bytes);
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

    public void validateOwnedReference(String reference) {
        Path file = path(reference);
        if (!reference.startsWith(ownerPrefix()) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("An image is unavailable. Select it again.");
        }
    }

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

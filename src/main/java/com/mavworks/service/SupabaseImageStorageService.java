package com.mavworks.service;

import com.mavworks.repository.UserRepository;
import com.mavworks.security.CurrentUser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@Profile("supabase")
public class SupabaseImageStorageService implements ImageStorageService {
    private final String storageUrl;
    private final String secretKey;
    private final String bucket;
    private final CurrentUser currentUser;
    private final UserRepository users;
    private final HttpClient client;

    public SupabaseImageStorageService(
            @Value("${mavworks.storage.url}") String projectUrl,
            @Value("${mavworks.storage.secret-key}") String secretKey,
            @Value("${mavworks.storage.bucket}") String bucket,
            CurrentUser currentUser,
            UserRepository users) {

        URI project = URI.create(projectUrl.trim());

        if (!"https".equalsIgnoreCase(project.getScheme())
                || project.getHost() == null
                || project.getUserInfo() != null
                || project.getQuery() != null
                || project.getFragment() != null
                || !(project.getPath().isEmpty()
                || project.getPath().equals("/"))) {
            throw new IllegalArgumentException(
                    "Configure the Supabase project HTTPS URL without an API path.");
        }

        if (!secretKey.trim().startsWith("sb_secret_")) {
            throw new IllegalArgumentException(
                    "Configure a Supabase backend secret key.");
        }

        if (!bucket.matches("[a-z0-9][a-z0-9_-]*")) {
            throw new IllegalArgumentException(
                    "Configure a valid storage bucket name.");
        }

        this.storageUrl = projectUrl.trim().replaceAll("/+$", "")
                + "/storage/v1";
        this.secretKey = secretKey.trim();
        this.bucket = bucket;
        this.currentUser = currentUser;
        this.users = users;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String store(InputStream content, String originalFilename,
                        String contentType) {
        String prefix = ownerPrefix();

        try {
            byte[] bytes = content.readNBytes(
                    ImageValidationService.MAX_BYTES + 1);
            String extension = ImageValidationService.validate(bytes);
            String reference = prefix + UUID.randomUUID() + "." + extension;

            String detectedType = extension.equals("png")
                    ? "image/png"
                    : "image/jpeg";

            HttpRequest upload = request("/object", reference)
                    .header("Content-Type", detectedType)
                    .header("x-upsert", "false")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .build();

            HttpResponse<byte[]> response = send(upload);
            requireSuccess(response, "upload");

            return reference;
        } catch (IOException ex) {
            throw new UncheckedIOException(
                    "Image could not be uploaded. Try again.", ex);
        }
    }

    @Override
    public InputStream open(String reference) throws IOException {
        ownerPrefix();

        HttpRequest download = request("/object/authenticated", reference)
                .GET()
                .build();

        HttpResponse<byte[]> response = send(download);

        if (response.statusCode() == 404) {
            throw new NoSuchFileException(reference);
        }

        requireSuccess(response, "download");

        return new ByteArrayInputStream(response.body());
    }

    @Override
    public void validateOwnedReference(String reference) {
        checkOwner(reference);

        try (InputStream image = open(reference)) {
            // Opening the image confirms that it exists in shared storage.
        } catch (NoSuchFileException ex) {
            throw new IllegalArgumentException(
                    "An image is unavailable. Select it again.", ex);
        } catch (IOException ex) {
            throw new UncheckedIOException(
                    "Image could not be verified. Try again.", ex);
        }
    }

    @Override
    public void delete(String reference) {
        checkOwner(reference);

        HttpRequest deletion = request("/object", reference)
                .DELETE()
                .build();

        try {
            HttpResponse<byte[]> response = send(deletion);

            if (response.statusCode() == 404) {
                return;
            }

            requireSuccess(response, "deletion");
        } catch (IOException ex) {
            throw new UncheckedIOException(
                    "Image could not be deleted. Try again.", ex);
        }
    }

    private String ownerPrefix() {
        return currentUser.username()
                .flatMap(users::findByUsername)
                .map(user -> user.getId() + "-")
                .orElseThrow(() -> new AccessDeniedException(
                        "Sign in to access images."));
    }

    private void validateReference(String reference) {
        if (reference == null
                || !reference.matches(
                "[0-9]+-[0-9a-f-]{36}\\.(jpg|png)")) {
            throw new IllegalArgumentException("Invalid image reference.");
        }
    }

    private void checkOwner(String reference) {
        validateReference(reference);

        if (!reference.startsWith(ownerPrefix())) {
            throw new AccessDeniedException(
                    "You can only change your own images.");
        }
    }

    private HttpRequest.Builder request(String route, String reference) {
        validateReference(reference);

        URI uri = URI.create(
                storageUrl + route + "/" + bucket + "/" + reference);

        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("apikey", secretKey);
    }

    private HttpResponse<byte[]> send(HttpRequest request) throws IOException {
        try {
            return client.send(
                    request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Image request was interrupted.", ex);
        }
    }

    private void requireSuccess(HttpResponse<?> response, String operation)
            throws IOException {
        int status = response.statusCode();

        if (status < 200 || status >= 300) {
            throw new IOException(
                    "Image " + operation + " failed (HTTP " + status + ").");
        }
    }
}
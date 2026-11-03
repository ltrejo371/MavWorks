package com.mavworks;

import com.mavworks.model.User;
import com.mavworks.repository.UserRepository;
import com.mavworks.security.CurrentUser;
import com.mavworks.service.SupabaseImageStorageService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.NoSuchFileException;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupabaseImageStorageTest {
    private static final String REFERENCE =
            "1-12345678-1234-1234-1234-123456789abc.png";

    private CurrentUser current;
    private HttpClient client;
    private HttpResponse<byte[]> response;
    private SupabaseImageStorageService storage;
    private byte[] png;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        current = mock(CurrentUser.class);
        UserRepository users = mock(UserRepository.class);
        client = mock(HttpClient.class);
        response = mock(HttpResponse.class);

        User owner = new User();
        ReflectionTestUtils.setField(owner, "id", 1L);
        when(current.username()).thenReturn(Optional.of("owner"));
        when(users.findByUsername("owner")).thenReturn(Optional.of(owner));

        storage = new SupabaseImageStorageService(
                "https://example.supabase.co",
                "sb_secret_test_only",
                "mavworks-images",
                current,
                users);

        ReflectionTestUtils.setField(storage, "client", client);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(
                2, 2, BufferedImage.TYPE_INT_RGB), "png", buffer);
        png = buffer.toByteArray();

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(png);
        doReturn(response).when(client).send(any(HttpRequest.class), any());
    }

    @Test
    void uploadUsesValidatedTypeAndGeneratedFilename() throws Exception {
        String reference = storage.store(
                new ByteArrayInputStream(png),
                "../../untrusted.jpg",
                "text/plain");

        assertTrue(reference.matches(
                "1-[0-9a-f-]{36}\\.png"));

        ArgumentCaptor<HttpRequest> request =
                ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any());

        HttpRequest sent = request.getValue();
        assertEquals("POST", sent.method());
        assertEquals(
                "/storage/v1/object/mavworks-images/" + reference,
                sent.uri().getPath());
        assertEquals("image/png",
                sent.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("false",
                sent.headers().firstValue("x-upsert").orElseThrow());
        assertEquals("sb_secret_test_only",
                sent.headers().firstValue("apikey").orElseThrow());
    }

    @Test
    void invalidImageIsRejectedBeforeAnyRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> storage.store(
                        new ByteArrayInputStream(new byte[] {1, 2, 3}),
                        "fake.png", "image/png"));

        verifyNoInteractions(client);
    }

    @Test
    void signedInUserCanReadAnotherOwnersImage() throws Exception {
        String otherImage =
                "2-12345678-1234-1234-1234-123456789abc.png";

        try (var image = storage.open(otherImage)) {
            assertArrayEquals(png, image.readAllBytes());
        }

        ArgumentCaptor<HttpRequest> request =
                ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any());

        assertEquals("GET", request.getValue().method());
        assertEquals(
                "/storage/v1/object/authenticated/mavworks-images/"
                        + otherImage,
                request.getValue().uri().getPath());
    }

    @Test
    void ownershipAndReferenceChecksPreventUnauthorizedRequests() {
        String otherImage =
                "2-12345678-1234-1234-1234-123456789abc.png";

        assertThrows(AccessDeniedException.class,
                () -> storage.validateOwnedReference(otherImage));
        assertThrows(AccessDeniedException.class,
                () -> storage.delete(otherImage));
        assertThrows(IllegalArgumentException.class,
                () -> storage.open("../secret.png"));

        when(current.username()).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> storage.open(REFERENCE));
        assertThrows(AccessDeniedException.class,
                () -> storage.store(
                        new ByteArrayInputStream(png), "image.png", "image/png"));

        verifyNoInteractions(client);
    }

    @Test
    void ownerCanValidateAndDeleteAnImage() throws Exception {
        storage.validateOwnedReference(REFERENCE);
        storage.delete(REFERENCE);

        ArgumentCaptor<HttpRequest> request =
                ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(2)).send(request.capture(), any());

        var sent = request.getAllValues();
        assertEquals("GET", sent.get(0).method());
        assertEquals("DELETE", sent.get(1).method());
        assertEquals(
                "/storage/v1/object/mavworks-images/" + REFERENCE,
                sent.get(1).uri().getPath());
    }

    @Test
    void missingImagesAreHandledWithoutBreakingCleanup() {
        when(response.statusCode()).thenReturn(404);

        assertThrows(NoSuchFileException.class,
                () -> storage.open(REFERENCE));
        assertThrows(IllegalArgumentException.class,
                () -> storage.validateOwnedReference(REFERENCE));
        assertDoesNotThrow(() -> storage.delete(REFERENCE));
    }

    @Test
    void serverFailuresAreReportedForAllOperations() {
        when(response.statusCode()).thenReturn(503);

        assertThrows(UncheckedIOException.class,
                () -> storage.store(
                        new ByteArrayInputStream(png), "image.png", "image/png"));
        assertThrows(IOException.class,
                () -> storage.open(REFERENCE));
        assertThrows(UncheckedIOException.class,
                () -> storage.validateOwnedReference(REFERENCE));
        assertThrows(UncheckedIOException.class,
                () -> storage.delete(REFERENCE));
    }

    @Test
    void networkFailureIsReportedAsUploadFailure() throws Exception {
        doThrow(new IOException("Connection failed"))
                .when(client).send(any(HttpRequest.class), any());

        assertThrows(UncheckedIOException.class,
                () -> storage.store(
                        new ByteArrayInputStream(png), "image.png", "image/png"));
    }

    @Test
    void interruptedRequestPreservesInterruptFlag() throws Exception {
        doThrow(new InterruptedException("Interrupted"))
                .when(client).send(any(HttpRequest.class), any());

        try {
            assertThrows(IOException.class,
                    () -> storage.open(REFERENCE));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
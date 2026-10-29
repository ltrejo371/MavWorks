package com.mavworks;

import com.mavworks.dto.CreateListingRequest;
import com.mavworks.model.*;
import com.mavworks.repository.UserRepository;
import com.mavworks.security.CurrentUser;
import com.mavworks.service.LocalImageStorageService;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreateListingTest {
    @TempDir Path directory;

    @Test
    void imagesAreValidatedStoredAndRestrictedToTheirUploader() throws Exception {
        var current = mock(CurrentUser.class);
        var users = mock(UserRepository.class);
        var user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 1L);
        when(current.username()).thenReturn(Optional.of("seller"));
        when(users.findByUsername("seller")).thenReturn(Optional.of(user));
        var storage = new LocalImageStorageService(directory.toString(), current, users);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "png", buffer);
        byte[] png = buffer.toByteArray();
        String reference = storage.store(new ByteArrayInputStream(png), "../../image.png", "image/png");
        storage.validateOwnedReference(reference);
        try (var stored = storage.open(reference)) { assertArrayEquals(png, stored.readAllBytes()); }
        assertThrows(IllegalArgumentException.class, () -> storage.store(
                new ByteArrayInputStream("not an image".getBytes()), "fake.png", "image/png"));
        assertThrows(IllegalArgumentException.class,
                () -> LocalImageStorageService.validate(new byte[LocalImageStorageService.MAX_BYTES + 1]));
        assertThrows(IllegalArgumentException.class, () -> storage.validateOwnedReference("../secret.png"));
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 2L);
        assertThrows(IllegalArgumentException.class, () -> storage.validateOwnedReference(reference));
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 1L);
        storage.delete(reference);
        assertFalse(java.nio.file.Files.exists(directory.resolve(reference)));
    }

    @Test
    void creationPreservesImageOrderAndAllowsFreeServices() {
        User owner = new User();
        var listing = owner.createListing(new CreateListingRequest("Tutoring", "Math help", BigDecimal.ZERO,
                ListingType.SERVICE, null, List.of("first", "second")));
        assertSame(owner, listing.getOwner());
        assertEquals("first", listing.getImages().getFirst().getStorageReference());
        assertEquals(1, listing.getImages().get(1).getDisplayOrder());
        assertThrows(IllegalArgumentException.class, () -> owner.createListing(new CreateListingRequest(
                "Tutoring", "Math help", BigDecimal.ONE, ListingType.SERVICE, null,
                Collections.nCopies(6, "image"))));
    }
}

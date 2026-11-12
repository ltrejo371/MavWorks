package com.mavworks;

import com.mavworks.model.*;
import com.mavworks.repository.*;
import com.mavworks.security.CurrentUser;
import com.mavworks.service.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListingImageManagementTest {
    private final CurrentUser current = mock(CurrentUser.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ListingRepository listings = mock(ListingRepository.class);
    private final ImageStorageService storage = mock(ImageStorageService.class);
    private final ListingManagementService service = new ListingManagementService(current, users, listings, storage);
    private Listing listing;

    @BeforeEach void setup() {
        User owner = new User();
        ReflectionTestUtils.setField(owner, "id", 1L);
        when(current.username()).thenReturn(Optional.of("owner"));
        when(users.findByUsername("owner")).thenReturn(Optional.of(owner));
        listing = new Listing();
        listing.setOwner(owner);
        listing.setType(ListingType.PRODUCT);
        ListingImage old = new ListingImage();
        old.setStorageReference("old.png");
        listing.getImages().add(old);
        when(listings.findForManagement(10L)).thenReturn(Optional.of(listing));
    }

    private void update(List<String> references) {
        service.updateDetails(10L, "Title", "Description", BigDecimal.TEN,
                ListingCategory.OTHER, null, references);
    }

    @Test void replacesImagesInSelectionOrderAndPreservesManagedCollection() {
        var collection = listing.getImages();
        update(List.of("new.png", "second.jpg"));
        assertSame(collection, listing.getImages());
        assertEquals(List.of("new.png", "second.jpg"), listing.getImages().stream()
                .map(ListingImage::getStorageReference).toList());
        assertEquals(List.of(0, 1), listing.getImages().stream().map(ListingImage::getDisplayOrder).toList());
        verify(storage).validateOwnedReference("new.png");
        verify(storage).validateOwnedReference("second.jpg");
    }

    @Test void detailsOnlyEditsRetainImages() {
        update(null);
        assertEquals("old.png", listing.getImages().getFirst().getStorageReference());
        verifyNoInteractions(storage);
    }

    @Test void invalidOrExcessImagesLeaveExistingImagesUntouched() {
        doThrow(new IllegalArgumentException("Unavailable image")).when(storage).validateOwnedReference("bad.png");
        assertThrows(IllegalArgumentException.class, () -> update(List.of("new.png", "bad.png")));
        assertThrows(IllegalArgumentException.class, () -> update(Collections.nCopies(6, "new.png")));
        assertEquals("old.png", listing.getImages().getFirst().getStorageReference());
    }

    @Test void otherUsersAndClosedListingsCannotReplaceImages() {
        User outsider = new User();
        ReflectionTestUtils.setField(outsider, "id", 2L);
        when(users.findByUsername("owner")).thenReturn(Optional.of(outsider));
        assertThrows(AccessDeniedException.class, () -> update(List.of("new.png")));
        when(users.findByUsername("owner")).thenReturn(Optional.of(listing.getOwner()));
        for (ListingStatus status : List.of(ListingStatus.SOLD, ListingStatus.REMOVED)) {
            listing.setStatus(status);
            assertThrows(IllegalArgumentException.class, () -> update(List.of("new.png")));
        }
        verifyNoInteractions(storage);
    }
}

package com.mavworks;

import com.mavworks.dto.SearchFilter;
import com.mavworks.model.*;
import com.mavworks.repository.*;
import com.mavworks.service.ListingCatalogService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "vaadin.launch-browser=false", "vaadin.devserver.enabled=false",
        "mavworks.seed-development-terms=false",
        "spring.datasource.url=jdbc:h2:mem:catalog-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class ListingCatalogTest {
    @Autowired ListingCatalogService catalog;
    @Autowired ListingRepository listings;
    @Autowired UserRepository users;
    @Autowired com.mavworks.service.AccountDashboardService dashboard;
    @Autowired com.mavworks.service.ListingService publishing;
    @Autowired com.mavworks.service.LocalImageStorageService imageStorage;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @org.junit.jupiter.api.io.TempDir static java.nio.file.Path imageDirectory;

    @org.springframework.test.context.DynamicPropertySource
    static void imageDirectory(org.springframework.test.context.DynamicPropertyRegistry properties) {
        properties.add("mavworks.images-directory", () -> imageDirectory.toString());
    }

    @Test
    void createListingPublishesToHomeAndOwnersDashboardAndRejectsInvalidInput() throws Exception {
        User seller = new User();
        seller.setUsername("publisher");
        seller.setEmail("publisher@mavs.uta.edu");
        seller.setHashedPassword("test-only");
        users.saveAndFlush(seller);
        var request = new com.mavworks.dto.CreateListingRequest(" New book ", " Useful book ",
                new BigDecimal("12.50"), ListingType.PRODUCT, null, null);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> publishing.createListing(request));
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "publisher", null, java.util.List.of()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try {
            var imageBytes = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2,
                    java.awt.image.BufferedImage.TYPE_INT_RGB), "png", imageBytes);
            String reference = imageStorage.store(new java.io.ByteArrayInputStream(imageBytes.toByteArray()),
                    "book.png", "image/png");
            var uploadedRequest = new com.mavworks.dto.CreateListingRequest(request.name(), request.description(),
                    request.price(), request.type(), request.category(), java.util.List.of(reference));
            Long id = publishing.createListing(uploadedRequest);
            entityManager.clear();
            var saved = listings.findById(id).orElseThrow();
            assertEquals(seller.getId(), saved.getOwner().getId());
            assertEquals("New book", saved.getName());
            assertEquals(ListingStatus.ACTIVE, saved.getStatus());
            assertEquals(ListingCategory.OTHER, saved.getCategory());
            assertEquals(reference, saved.getImages().getFirst().getStorageReference());
            assertEquals(id, catalog.search(new SearchFilter("New book", null), null).getFirst().id());
            assertEquals(id, dashboard.load().listings().getFirst().id());
            assertEquals(id, publishing.getListingDetails(id).orElseThrow().getId());
            long count = listings.count();
            var missing = assertThrows(IllegalArgumentException.class, () -> publishing.createListing(
                    new com.mavworks.dto.CreateListingRequest(" ", "", null, null, null, null)));
            assertTrue(missing.getMessage().contains("name"));
            assertTrue(missing.getMessage().contains("description"));
            assertTrue(missing.getMessage().contains("Product or Service"));
            for (String price : java.util.List.of("-1", "1.001", "10000000000")) {
                assertThrows(IllegalArgumentException.class, () -> publishing.createListing(
                        new com.mavworks.dto.CreateListingRequest("Book", "Description", new BigDecimal(price),
                                ListingType.PRODUCT, null, null)));
            }
            assertEquals(count, listings.count());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void homeFiltersActiveListingsByTextCategoryAndType() {
        User seller = new User();
        seller.setUsername("seller");
        seller.setEmail("seller@mavs.uta.edu");
        seller.setHashedPassword("test-only");
        users.saveAndFlush(seller);
        add(seller, "Calculus book", "Used textbook", ListingType.PRODUCT, ListingStatus.ACTIVE);
        add(seller, "Tutoring", "Help with CALCULUS", ListingType.SERVICE, ListingStatus.ACTIVE);
        add(seller, "Removed listing", "Calculus", ListingType.PRODUCT, ListingStatus.REMOVED);
        add(seller, "Sold book", "Calculus", ListingType.PRODUCT, ListingStatus.SOLD);

        assertEquals(2, catalog.search(new SearchFilter("", null), null).size());
        assertEquals(2, catalog.search(new SearchFilter(" calculus ", ListingCategory.OTHER), null).size());
        var products = catalog.search(new SearchFilter("calculus", ListingCategory.OTHER), ListingType.PRODUCT);
        assertEquals(1, products.size());
        assertEquals("Calculus book", products.getFirst().name());
        assertEquals(1, catalog.search(new SearchFilter("", null), ListingType.SERVICE).size());
        assertTrue(catalog.search(new SearchFilter("no match", null), null).isEmpty());
        assertTrue(catalog.search(new SearchFilter("%", null), null).isEmpty());
    }

    private void add(User owner, String name, String description, ListingType type, ListingStatus status) {
        Listing listing = new Listing();
        listing.setOwner(owner);
        listing.setName(name);
        listing.setDescription(description);
        listing.setType(type);
        listing.setStatus(status);
        listing.setPrice(new BigDecimal("15.00"));
        listings.saveAndFlush(listing);
    }

    @Test
    void personalDashboardOnlyReturnsSignedInUsersAccountAndListings() {
        User first = new User();
        first.setUsername("first");
        first.setEmail("first@mavs.uta.edu");
        first.setHashedPassword("test-only");
        users.saveAndFlush(first);
        User second = new User();
        second.setUsername("second");
        second.setEmail("second@mavs.uta.edu");
        second.setHashedPassword("test-only");
        users.saveAndFlush(second);
        add(first, "My listing", "Mine", ListingType.PRODUCT, ListingStatus.ACTIVE);
        add(second, "Their listing", "Theirs", ListingType.PRODUCT, ListingStatus.ACTIVE);
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "first", null, java.util.List.of()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try {
            var data = dashboard.load();
            assertEquals("first", data.username());
            assertEquals("first@mavs.uta.edu", data.email());
            assertEquals(1, data.listings().size());
            assertEquals("My listing", data.listings().getFirst().name());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> dashboard.load());
    }
}

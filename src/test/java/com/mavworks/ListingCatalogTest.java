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

    @Test
    void homeFiltersPublishedListingsByTextCategoryAndType() {
        User seller = new User();
        seller.setUsername("seller");
        seller.setEmail("seller@mavs.uta.edu");
        seller.setHashedPassword("test-only");
        users.saveAndFlush(seller);
        add(seller, "Calculus book", "Used textbook", ListingType.PRODUCT, ListingStatus.PUBLISHED);
        add(seller, "Tutoring", "Help with CALCULUS", ListingType.SERVICE, ListingStatus.PUBLISHED);
        add(seller, "Private draft", "Calculus", ListingType.PRODUCT, ListingStatus.DRAFT);
        add(seller, "Sold book", "Calculus", ListingType.PRODUCT, ListingStatus.UNAVAILABLE);

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
        add(first, "My listing", "Mine", ListingType.PRODUCT, ListingStatus.PUBLISHED);
        add(second, "Their listing", "Theirs", ListingType.PRODUCT, ListingStatus.PUBLISHED);
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

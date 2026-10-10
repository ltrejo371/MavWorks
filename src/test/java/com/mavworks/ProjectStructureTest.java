package com.mavworks;

import com.mavworks.model.*;
import com.mavworks.repository.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "vaadin.launch-browser=false", "mavworks.seed-development-terms=false",
        "vaadin.devserver.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:structure-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class ProjectStructureTest {
    @Autowired UserRepository users;
    @Autowired ListingRepository listings;
    @Autowired TermsOfServiceRepository terms;
    @Autowired TOSAgreementRepository agreements;
    @Autowired EntityManager entityManager;

    @Test
    void persistsAccountListingImagesAndTermsRelationships() {
        User user = new User();
        user.setUsername("structure-test");
        user.setEmail("structure-test@mavs.uta.edu");
        user.setHashedPassword("test-only-placeholder");
        user.getProfile().setName("Test Student");
        users.saveAndFlush(user);

        TermsOfService currentTerms = new TermsOfService();
        currentTerms.setVersion("test-v1");
        currentTerms.setContent("Test terms");
        currentTerms.setEffectiveAt(Instant.now().minusSeconds(60));
        terms.saveAndFlush(currentTerms);

        TOSAgreement agreement = new TOSAgreement();
        agreement.setUser(user);
        agreement.setTerms(currentTerms);
        agreements.saveAndFlush(agreement);

        ListingImage image = new ListingImage();
        image.setStorageReference("test/book.png");
        image.setDisplayOrder(0);
        Listing listing = new Listing();
        listing.setOwner(user);
        listing.setName("Book");
        listing.setDescription("A test listing");
        listing.setPrice(new BigDecimal("12.50"));
        listing.setType(ListingType.PRODUCT);
        listing.setStatus(ListingStatus.PUBLISHED);
        listing.setImages(List.of(image));
        listings.saveAndFlush(listing);
        Long userId = user.getId();
        Long termsId = currentTerms.getId();
        Long listingId = listing.getId();
        entityManager.clear();

        assertEquals("Test Student",
                users.findByUsername("structure-test").orElseThrow().getProfile().getName());
        assertTrue(users.existsByEmail("structure-test@mavs.uta.edu"));
        assertTrue(agreements.existsByUserIdAndTermsId(userId, termsId));
        assertEquals(termsId, terms.findFirstByEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                Instant.now()).orElseThrow().getId());
        assertEquals(1, listings.findByOwnerId(userId).size());
        assertEquals(1, listings.findByStatus(ListingStatus.PUBLISHED).size());
        Listing loaded = listings.findById(listingId).orElseThrow();
        assertEquals(userId, loaded.getOwner().getId());
        assertEquals("test/book.png", loaded.getImages().getFirst().getStorageReference());
        loaded.getImages().clear();
        listings.flush();
        entityManager.clear();
        assertEquals(0L, entityManager.createQuery(
                "select count(i) from ListingImage i", Long.class).getSingleResult());
    }
}

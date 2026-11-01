package com.mavworks;

import com.mavworks.dto.CreateReviewRequest;
import com.mavworks.model.*;
import com.mavworks.repository.*;
import com.mavworks.service.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "vaadin.launch-browser=false", "vaadin.devserver.enabled=false", "mavworks.seed-development-terms=false",
        "spring.datasource.url=jdbc:h2:mem:reviews-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.default_schema=mavworks",
        "spring.flyway.enabled=true", "spring.flyway.locations=classpath:db/migration/postgresql",
        "spring.flyway.default-schema=mavworks", "spring.flyway.schemas=mavworks", "spring.flyway.create-schemas=true"
})
@Transactional
class ReviewWorkflowTest {
    @Autowired UserRepository users;
    @Autowired ListingRepository listings;
    @Autowired ConversationRepository conversations;
    @Autowired ReviewRepository reviews;
    @Autowired ReviewService service;
    @Autowired ConversationService sales;
    @Autowired MessagingService messaging;
    @Autowired EntityManager entityManager;
    @Autowired BlockService blocks;
    User buyer, provider, stranger;
    Listing listing;
    Conversation conversation;

    @BeforeEach
    void setup() {
        buyer = account("buyer");
        provider = account("provider");
        stranger = account("stranger");
        listing = listing(provider, "Tutoring");
        conversation = conversations.saveAndFlush(new Conversation(buyer, listing));
        login(buyer);
    }

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void publishesReviewOnCorrectProfileAndPersistsAfterReload() {
        completeSale();
        assertEquals(List.of(listing.getId()), service.getReviewableListings(provider.getId()).stream().map(l -> l.id()).toList());
        Long id = service.leaveReview(provider.getId(), request(5, "  Helpful tutor!  "));
        entityManager.clear();
        var published = service.getProviderProfile(provider.getId()).orElseThrow().reviews();
        assertEquals(1, published.size());
        assertEquals(id, published.getFirst().id());
        assertEquals("buyer", published.getFirst().author());
        assertEquals("Helpful tutor!", published.getFirst().comment());
        assertEquals(5, published.getFirst().rating());
        assertTrue(service.getProviderProfile(stranger.getId()).orElseThrow().reviews().isEmpty());
        assertTrue(service.getReviewableListings(provider.getId()).isEmpty());
        login(stranger);
        assertEquals(1, service.getProviderProfile(provider.getId()).orElseThrow().reviews().size());
    }

    @Test void commentIsOptionalAndDuplicateReviewsAreRejected() {
        completeSale();
        service.leaveReview(provider.getId(), request(1, null));
        assertEquals("", reviews.findAll().getFirst().getComment());
        assertThrows(IllegalArgumentException.class, () -> service.leaveReview(provider.getId(), request(4, "again")));
        assertEquals(1, reviews.count());
    }

    @Test void openClosedWithoutSaleAndConfirmedButOpenConversationsDoNotQualify() {
        assertDenied();
        conversation.confirmSale(provider);
        conversations.flush();
        assertDenied();
        // A different listing with only a closed conversation must not inherit eligibility.
        var second = listing(provider, "Book");
        var closed = new Conversation(buyer, second);
        closed.closeConversation();
        conversations.saveAndFlush(closed);
        assertTrue(service.getReviewableListings(provider.getId()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.leaveReview(provider.getId(),
                new CreateReviewRequest(second.getId(), 5, "")));
        assertThrows(IllegalArgumentException.class, () -> closed.confirmSale(provider));
        assertEquals(0, reviews.count());
    }

    @Test void noConversationOrSoldStatusAloneDoesNotQualify() {
        listing.setStatus(ListingStatus.SOLD);
        listings.flush();
        login(stranger);
        assertDenied();
        login(buyer);
        assertDenied();
    }

    @Test void wrongListingWrongProviderOtherBuyerAndSelfReviewsAreRejected() {
        completeSale();
        var otherListing = listing(provider, "Another listing");
        assertThrows(IllegalArgumentException.class, () -> service.leaveReview(provider.getId(),
                new CreateReviewRequest(otherListing.getId(), 5, "")));
        assertThrows(IllegalArgumentException.class, () -> service.leaveReview(stranger.getId(), request(5, "")));
        login(stranger);
        assertDenied();
        login(provider);
        assertDenied();
        assertThrows(IllegalArgumentException.class, () -> provider.leaveReview(conversation, 5, ""));
    }

    @Test void requiresAuthenticationAndOnlyProviderCanConfirmSale() {
        assertThrows(AccessDeniedException.class, () -> sales.confirmSaleAndClose(conversation.getId()));
        login(stranger);
        assertThrows(AccessDeniedException.class, () -> sales.confirmSaleAndClose(conversation.getId()));
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> service.leaveReview(provider.getId(), request(5, "")));
        assertThrows(AccessDeniedException.class, () -> service.getReviewableListings(provider.getId()));
        assertThrows(AccessDeniedException.class, () -> sales.confirmSaleAndClose(conversation.getId()));
        assertNull(conversation.getSaleConfirmedAt());
        assertNull(conversation.getClosedAt());
    }

    @Test void validatesListingRatingAndCommentBeforeSaving() {
        completeSale();
        assertThrows(IllegalArgumentException.class, () -> service.leaveReview(provider.getId(), null));
        assertThrows(IllegalArgumentException.class, () -> service.leaveReview(provider.getId(), new CreateReviewRequest(null, 5, "")));
        for (Integer rating : new Integer[] {null, 0, -1, 6}) {
            assertThrows(IllegalArgumentException.class, () -> service.leaveReview(provider.getId(), request(rating, "")));
        }
        assertThrows(IllegalArgumentException.class, () -> service.leaveReview(provider.getId(), request(5, "x".repeat(2001))));
        assertEquals(0, reviews.count());
        service.leaveReview(provider.getId(), request(5, "x".repeat(2000)));
        assertEquals(1, reviews.count());
    }

    @Test void databasePreventsDuplicateReviewsEvenIfApplicationCheckIsBypassed() {
        completeSale();
        service.leaveReview(provider.getId(), request(5, ""));
        assertThrows(RuntimeException.class, () -> entityManager.createNativeQuery("""
                insert into mavworks.reviews (author_id, listing_id, rating, comment, created_at)
                values (:author, :listing, 4, 'duplicate', CURRENT_TIMESTAMP)
                """).setParameter("author", buyer.getId()).setParameter("listing", listing.getId()).executeUpdate());
    }

    @Test void multipleCompletedConversationsStillOfferOneListing() {
        completeSale();
        var second = conversations.saveAndFlush(new Conversation(buyer, listing));
        messaging.sendMessage(second.getId(), "Following up about this listing.");
        login(provider);
        sales.confirmSaleAndClose(second.getId());
        login(buyer);
        assertEquals(1, service.getReviewableListings(provider.getId()).size());
    }

    @Test void deletingProviderCascadesReviewsAndConversations() {
        completeSale();
        service.leaveReview(provider.getId(), request(5, ""));
        entityManager.flush();
        entityManager.createNativeQuery("delete from mavworks.app_users where id = :id")
                .setParameter("id", provider.getId()).executeUpdate();
        entityManager.clear();
        assertEquals(0, reviews.count());
        assertEquals(0, conversations.count());
        assertTrue(users.existsById(buyer.getId()));
    }

    @Test
    void publicProfileReturnsOnlyItsProvidersActiveListings() {
        listing(stranger, "Someone else's listing");

        var soldListing = listing(provider, "Sold item");
        soldListing.setStatus(ListingStatus.SOLD);

        var expiredListing = listing(provider, "Expired item");
        expiredListing.setStatus(ListingStatus.EXPIRED);

        var removedListing = listing(provider, "Removed item");
        removedListing.setStatus(ListingStatus.REMOVED);

        entityManager.flush();
        entityManager.clear();

        var result = service.getProviderProfile(provider.getId())
                .orElseThrow();

        var actualIds = result.listings().stream()
                .map(item -> item.id())
                .toList();

        assertEquals(List.of(listing.getId()), actualIds);
    }

    @Test
    void publicProfileWithNoActiveListingsReturnsAnEmptyList() {
        listing.setStatus(ListingStatus.SOLD);

        entityManager.flush();
        entityManager.clear();

        var result = service.getProviderProfile(provider.getId())
                .orElseThrow();

        assertTrue(result.listings().isEmpty());
        assertEquals(0, result.reviewCount());
        assertNull(result.averageRating());
    }

    @Test
    void blockingReviewerHidesReviewButPreservesRating() {
        // The buyer completes a sale and leaves a review.
        completeSale();
        service.leaveReview(provider.getId(), request(4, "Helpful tutor"));

        // Another user views the provider before blocking the reviewer.
        login(stranger);

        var before = service.getProviderProfile(provider.getId())
                .orElseThrow();

        assertEquals(1, before.reviews().size());
        assertEquals(1, before.reviewCount());
        assertEquals(4.0, before.averageRating().doubleValue(), 0.001);

        // Block the reviewer, not the provider.
        blocks.blockUser(buyer.getId());

        entityManager.flush();
        entityManager.clear();

        var after = service.getProviderProfile(provider.getId())
                .orElseThrow();

        assertTrue(after.reviews().isEmpty());
        assertEquals(1, after.reviewCount());
        assertEquals(4.0, after.averageRating().doubleValue(), 0.001);
    }

    @Test
    void profileCalculatesAverageAcrossMultipleReviews() {
        // The original buyer leaves a 4-star review.
        completeSale();
        service.leaveReview(provider.getId(), request(4, "Good"));

        // A second buyer completes a sale for the same listing.
        login(stranger);
        Long secondConversationId =
                messaging.startConversation(listing.getId());

        messaging.sendMessage(secondConversationId, "Thanks for the tutoring.");

        login(provider);
        sales.confirmSaleAndClose(secondConversationId);

        login(stranger);
        service.leaveReview(provider.getId(), request(5, "Excellent"));

        entityManager.flush();
        entityManager.clear();

        var result = service.getProviderProfile(provider.getId())
                .orElseThrow();

        assertEquals(2, result.reviewCount());
        assertEquals(4.5, result.averageRating().doubleValue(), 0.001);
    }

    private void assertDenied() {
        assertTrue(service.getReviewableListings(provider.getId()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.leaveReview(provider.getId(), request(5, "")));
    }
    private CreateReviewRequest request(Integer rating, String comment) { return new CreateReviewRequest(listing.getId(), rating, comment); }
    private void completeSale() {
        login(buyer);
        messaging.sendMessage(conversation.getId(), "We agreed on the sale.");
        login(provider);
        sales.confirmSaleAndClose(conversation.getId());
        login(buyer);
    }
    private void login(User user) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user.getUsername(), null, List.of()));
        SecurityContextHolder.setContext(context);
    }
    private User account(String name) {
        var user = new User();
        user.setUsername(name);
        user.setEmail(name + "@mavs.uta.edu");
        user.setHashedPassword("test-only");
        return users.saveAndFlush(user);
    }
    private Listing listing(User owner, String name) {
        var listing = new Listing();
        listing.setOwner(owner);
        listing.setName(name);
        listing.setDescription("Test listing");
        listing.setPrice(BigDecimal.TEN);
        listing.setType(ListingType.SERVICE);
        return listings.saveAndFlush(listing);
    }
}

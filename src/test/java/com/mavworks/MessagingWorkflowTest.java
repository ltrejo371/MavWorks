package com.mavworks;

import com.mavworks.dto.CreateReviewRequest;
import com.mavworks.model.*;
import com.mavworks.repository.*;
import com.mavworks.service.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "vaadin.launch-browser=false", "vaadin.devserver.enabled=false", "mavworks.seed-development-terms=false",
        "spring.datasource.url=jdbc:h2:mem:messaging-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.default_schema=mavworks",
        "spring.flyway.enabled=true", "spring.flyway.locations=classpath:db/migration/postgresql",
        "spring.flyway.default-schema=mavworks", "spring.flyway.schemas=mavworks", "spring.flyway.create-schemas=true"
})
class MessagingWorkflowTest {
    @Autowired UserRepository users;
    @Autowired ListingRepository listings;
    @Autowired ConversationRepository conversations;
    @Autowired MessageRepository messages;
    @Autowired UserBlockRepository blocks;
    @Autowired MessagingService service;
    @Autowired ConversationService sales;
    @Autowired ReviewService reviews;
    @Autowired BlockService blocking;
    @Autowired ListingService listingService;
    @Autowired ListingCatalogService catalog;
    @Autowired ListingManagementService management;
    @Autowired AccountDashboardService dashboard;

    User buyer, provider, outsider;
    Listing listing;

    @BeforeEach void setup() {
        users.deleteAll();
        buyer = account("buyer");
        provider = account("provider");
        outsider = account("outsider");
        listing = new Listing();
        listing.setOwner(provider);
        listing.setName("Book");
        listing.setDescription("A textbook");
        listing.setPrice(BigDecimal.TEN);
        listing.setType(ListingType.PRODUCT);
        listing = listings.saveAndFlush(listing);
        login(buyer);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void profileShowsOnlyAvailableListingsAndHonorsBlocks() {
        ListingImage preview = new ListingImage(); preview.setStorageReference("profile-preview.png");
        listing.getImages().add(preview); listings.saveAndFlush(listing);
        var visible = reviews.getProviderProfile(provider.getId()).orElseThrow().listings();
        assertEquals(1, visible.size());
        assertEquals(listing.getId(), visible.getFirst().id());
        assertEquals("profile-preview.png", visible.getFirst().imageReference());
        assertTrue(reviews.getProviderProfile(buyer.getId()).orElseThrow().listings().isEmpty());
        listing.setStatus(ListingStatus.SOLD); listings.saveAndFlush(listing);
        assertTrue(reviews.getProviderProfile(provider.getId()).orElseThrow().listings().isEmpty());
        listing.setStatus(ListingStatus.REMOVED); listings.saveAndFlush(listing);
        assertTrue(reviews.getProviderProfile(provider.getId()).orElseThrow().listings().isEmpty());
        listing.setStatus(ListingStatus.ACTIVE); listings.saveAndFlush(listing);
        blocking.blockUser(provider.getId());
        assertTrue(reviews.getProviderProfile(provider.getId()).isEmpty());
        assertEquals(1, catalog.search(new com.mavworks.dto.SearchFilter("", null), null).size());
    }

    @Test void inboxIncludesListingPreviewAndLatestMessageFromEitherParticipant() {
        ListingImage image = new ListingImage(); image.setStorageReference("preview.png"); image.setDisplayOrder(0);
        listing.getImages().add(image); listings.saveAndFlush(listing);
        Long id = service.startConversation(listing.getId());
        var empty = service.getMyConversations().getFirst();
        assertEquals("Book", empty.listingName());
        assertEquals("provider", empty.otherUser());
        assertEquals("preview.png", empty.imageReference());
        assertNull(empty.lastMessageAt());
        Long first = service.sendMessage(id, "Is this available?");
        assertEquals(messages.findById(first).orElseThrow().getSentAt(), service.getMyConversations().getFirst().lastMessageAt());
        login(provider);
        assertEquals("buyer", service.getMyConversations().getFirst().otherUser());
        Long reply = service.sendMessage(id, "Yes, it is.");
        var latest = messages.findById(reply).orElseThrow().getSentAt();
        assertEquals(latest, service.getMyConversations().getFirst().lastMessageAt());
        login(buyer);
        assertEquals(latest, service.getMyConversations().getFirst().lastMessageAt());
        login(outsider);
        assertTrue(service.getMyConversations().isEmpty());
    }

    @Test void inboxOrdersByLastMessageAndHandlesListingsWithoutImages() {
        Long first = service.startConversation(listing.getId());
        Listing secondListing = new Listing(); secondListing.setOwner(provider); secondListing.setName("Chair");
        secondListing.setDescription("Desk chair"); secondListing.setPrice(BigDecimal.TEN); secondListing.setType(ListingType.PRODUCT);
        secondListing = listings.saveAndFlush(secondListing);
        Long second = service.startConversation(secondListing.getId());
        service.sendMessage(second, "Hello");
        assertEquals(second, service.getMyConversations().getFirst().id());
        assertNull(service.getMyConversations().getFirst().imageReference());
        service.sendMessage(first, "Available?");
        assertEquals(first, service.getMyConversations().getFirst().id());
        login(provider);
        sales.confirmSaleAndClose(first);
        assertTrue(service.getMyConversations().getFirst().closed());
    }

    @Test void blockingIsIdempotentAndRejectsSelfMissingAndAnonymousAccounts() {
        assertThrows(IllegalArgumentException.class, () -> blocking.blockUser(buyer.getId()));
        assertThrows(IllegalArgumentException.class, () -> blocking.blockUser(null));
        assertThrows(IllegalArgumentException.class, () -> blocking.blockUser(Long.MAX_VALUE));
        assertEquals(BlockService.Result.BLOCKED, blocking.blockUser(provider.getId()));
        assertEquals(BlockService.Result.ALREADY_BLOCKED, blocking.blockUser(provider.getId()));
        assertEquals(1, blocks.count());
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> blocking.blockUser(outsider.getId()));
    }

    @Test void blockHidesAccountsReviewsAndConversationsButRetainsListingsAndHistory() {
        listing.setType(ListingType.SERVICE);
        listings.saveAndFlush(listing);
        Long id = service.startConversation(listing.getId());
        service.sendMessage(id, "Agreed");
        login(provider);
        sales.confirmSaleAndClose(id);
        login(buyer);
        reviews.leaveReview(provider.getId(), new CreateReviewRequest(listing.getId(), 5, "Great"));
        blocking.blockUser(provider.getId());
        assertTrue(reviews.getProviderProfile(provider.getId()).isEmpty());
        assertTrue(blocking.visibleUser(provider.getId()).isEmpty());
        assertTrue(reviews.getReviewableListings(provider.getId()).isEmpty());
        assertThrows(AccessDeniedException.class, () -> reviews.leaveReview(provider.getId(),
                new CreateReviewRequest(listing.getId(), 4, "Changed")));
        assertTrue(service.getMyConversations().isEmpty());
        assertThrows(MessagingBlockedException.class, () -> service.getChat(id, null));
        assertTrue(listingService.getListingDetails(listing.getId()).isPresent());
        assertEquals(1, catalog.search(new com.mavworks.dto.SearchFilter("", null), null).size());
        login(provider);
        assertTrue(reviews.getProviderProfile(buyer.getId()).isEmpty());
        assertTrue(blocking.visibleUser(buyer.getId()).isEmpty());
        assertTrue(service.getMyConversations().isEmpty());
        assertTrue(reviews.getProviderProfile(provider.getId()).orElseThrow().reviews().isEmpty());
        assertThrows(MessagingBlockedException.class, () -> service.sendMessage(id, "No contact"));
        assertEquals(1, messages.count());
        assertEquals(1, conversations.count());
        login(outsider);
        assertEquals(1, reviews.getProviderProfile(provider.getId()).orElseThrow().reviews().size());
    }

    @Test void blockedAuthorsAreHiddenOnThirdPartyProfilesToo() {
        var otherListing = new Listing();
        otherListing.setOwner(outsider);
        otherListing.setName("Service");
        otherListing.setDescription("Help");
        otherListing.setPrice(BigDecimal.ONE);
        otherListing.setType(ListingType.SERVICE);
        listings.saveAndFlush(otherListing);
        Long id = service.startConversation(otherListing.getId());
        service.sendMessage(id, "Thanks");
        login(outsider);
        sales.confirmSaleAndClose(id);
        login(buyer);
        reviews.leaveReview(outsider.getId(), new CreateReviewRequest(otherListing.getId(), 5, "Excellent"));
        login(provider);
        assertEquals(1, reviews.getProviderProfile(outsider.getId()).orElseThrow().reviews().size());
        blocking.blockUser(buyer.getId());
        assertTrue(reviews.getProviderProfile(outsider.getId()).orElseThrow().reviews().isEmpty());
        login(outsider);
        assertEquals(1, reviews.getProviderProfile(outsider.getId()).orElseThrow().reviews().size());
    }

    @Test void concurrentConfirmationsCreateOnlyOneBlock() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<BlockService.Result> action = () -> {
                login(buyer);
                try {
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                    return blocking.blockUser(provider.getId());
                } finally { SecurityContextHolder.clearContext(); }
            };
            var first = executor.submit(action);
            var second = executor.submit(action);
            start.countDown();
            assertEquals(java.util.Set.of(BlockService.Result.BLOCKED, BlockService.Result.ALREADY_BLOCKED),
                    java.util.Set.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)));
        }
        assertEquals(1, blocks.count());
    }

    @Test void bothPartiesCanReplyAndRevisitPersistedHistory() {
        Long id = service.startConversation(listing.getId());
        assertEquals(id, service.startConversation(listing.getId()));
        assertTrue(service.getChat(id, null).messages().isEmpty());
        Long first = service.sendMessage(id, "  Is this available?  ");
        login(provider);
        assertEquals(id, service.getMyConversations().getFirst().id());
        var chat = service.getChat(id, null);
        assertTrue(chat.provider());
        assertEquals("Is this available?", chat.messages().getFirst().body());
        assertEquals(buyer.getId(), chat.messages().getFirst().senderId());
        service.sendMessage(id, "Yes, it is!");
        login(buyer);
        assertEquals(id, service.getMyConversations().getFirst().id());
        assertEquals(2, service.getChat(id, null).messages().size());
        var newMessages = service.getChat(id, first).messages();
        assertEquals(1, newMessages.size());
        assertEquals(provider.getId(), newMessages.getFirst().senderId());
        assertEquals(2, messages.count());
        assertEquals(1, conversations.count());
    }

    @Test void anonymousAndUnrelatedUsersCannotReadSendOrConfirm() {
        Long id = service.startConversation(listing.getId());
        service.sendMessage(id, "Private message");
        login(outsider);
        assertTrue(service.getMyConversations().isEmpty());
        assertThrows(AccessDeniedException.class, () -> service.getChat(id, null));
        assertThrows(AccessDeniedException.class, () -> service.getOlderMessages(id, null));
        assertThrows(AccessDeniedException.class, () -> service.sendMessage(id, "Intrusion"));
        assertThrows(AccessDeniedException.class, () -> sales.confirmSaleAndClose(id));
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> service.startConversation(listing.getId()));
        assertThrows(AccessDeniedException.class, () -> service.getChat(id, null));
        assertThrows(AccessDeniedException.class, () -> service.sendMessage(id, "Intrusion"));
        assertThrows(AccessDeniedException.class, () -> service.getMyConversations());
        assertEquals(1, messages.count());
    }

    @Test void blocksInEitherDirectionPreventStartingReadingAndSending() {
        Long id = service.startConversation(listing.getId());
        for (var pair : List.of(List.of(buyer, provider), List.of(provider, buyer))) {
            blocks.saveAndFlush(new UserBlock(pair.get(0), pair.get(1)));
            for (User participant : List.of(buyer, provider)) {
                login(participant);
                assertThrows(MessagingBlockedException.class, () -> service.getChat(id, null));
                assertThrows(MessagingBlockedException.class, () -> service.getOlderMessages(id, null));
                assertThrows(MessagingBlockedException.class, () -> service.sendMessage(id, "Blocked"));
            }
            login(buyer);
            assertThrows(MessagingBlockedException.class, () -> service.startConversation(listing.getId()));
            blocks.deleteAll();
        }
        assertEquals(1, conversations.count());
        assertEquals(0, messages.count());
    }

    @Test void noConversationIsCreatedForBlockedOrSelfMessages() {
        blocks.saveAndFlush(new UserBlock(provider, buyer));
        assertThrows(MessagingBlockedException.class, () -> service.startConversation(listing.getId()));
        login(provider);
        assertThrows(IllegalArgumentException.class, () -> service.startConversation(listing.getId()));
        assertEquals(0, conversations.count());
    }

    @Test void rejectsBlankAndOversizeMessagesAndAcceptsBoundaryLength() {
        Long id = service.startConversation(listing.getId());
        for (String value : new String[] {null, "", " \n\t ", "x".repeat(4001)}) {
            assertThrows(IllegalArgumentException.class, () -> service.sendMessage(id, value));
        }
        assertEquals(0, messages.count());
        service.sendMessage(id, "x".repeat(4000));
        assertEquals(4000, service.getChat(id, null).messages().getFirst().body().length());
    }

    @Test void providerConfirmationClosesChatAndUnlocksReviews() {
        Long id = service.startConversation(listing.getId());
        login(provider);
        assertThrows(IllegalArgumentException.class, () -> sales.confirmSaleAndClose(id));
        login(buyer);
        service.sendMessage(id, "Agreed, thank you.");
        assertTrue(reviews.getReviewableListings(provider.getId()).isEmpty());
        assertThrows(AccessDeniedException.class, () -> sales.confirmSaleAndClose(id));
        login(provider);
        sales.confirmSaleAndClose(id);
        assertTrue(service.getChat(id, null).closed());
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(id, "Too late"));
        login(buyer);
        assertEquals(id, service.startConversation(listing.getId()));
        assertEquals(1, service.getChat(id, null).messages().size());
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(id, "Too late"));
        reviews.leaveReview(provider.getId(), new CreateReviewRequest(listing.getId(), 5, "Great provider"));
        assertEquals(1, reviews.getProviderProfile(provider.getId()).orElseThrow().reviews().size());
    }

    @Test void concurrentlyOpeningListingCreatesOnlyOneConversation() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Long> task = () -> {
                login(buyer);
                try {
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                    return service.startConversation(listing.getId());
                } finally { SecurityContextHolder.clearContext(); }
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            start.countDown();
            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, conversations.count());
    }

    @Test void historyIsPagedAndChangesOfListingStatusDoNotLoseConversation() {
        Long id = service.startConversation(listing.getId());
        for (int i = 0; i < 105; i++) service.sendMessage(id, "Message " + i);
        var latest = service.getChat(id, null).messages();
        assertEquals(100, latest.size());
        assertEquals("Message 5", latest.getFirst().body());
        var older = service.getOlderMessages(id, latest.getFirst().id());
        assertEquals(5, older.size());
        assertEquals("Message 0", older.getFirst().body());
        assertEquals(100, service.getChat(id, 0L).messages().size());
        listing.setStatus(ListingStatus.SOLD);
        listings.saveAndFlush(listing);
        assertEquals(id, service.startConversation(listing.getId()));
        login(outsider);
        assertThrows(IllegalArgumentException.class, () -> service.startConversation(listing.getId()));
    }

    @Test void deletingProviderCascadesMessagesAndBlocks() {
        Long id = service.startConversation(listing.getId());
        service.sendMessage(id, "Hello");
        blocks.saveAndFlush(new UserBlock(provider, buyer));
        users.deleteById(provider.getId());
        assertEquals(0, messages.count());
        assertEquals(0, conversations.count());
        assertEquals(0, blocks.count());
        assertTrue(users.existsById(buyer.getId()));
    }

    @Test
    void confirmingProductSaleRemovesItFromAvailableListings() {
        // The setup already creates a PRODUCT listing.
        Long conversationId = service.startConversation(listing.getId());
        service.sendMessage(conversationId, "Agreed on the sale.");

        login(provider);
        sales.confirmSaleAndClose(conversationId);

        var savedListing = listings.findById(listing.getId()).orElseThrow();
        assertEquals(ListingStatus.SOLD, savedListing.getStatus());

        login(buyer);

        assertTrue(catalog.search(
                new com.mavworks.dto.SearchFilter("", null), null).isEmpty());

        assertTrue(reviews.getProviderProfile(provider.getId())
                .orElseThrow().listings().isEmpty());

        assertTrue(service.getChat(conversationId, null).closed());

        assertEquals(
                List.of(listing.getId()),
                reviews.getReviewableListings(provider.getId()).stream()
                        .map(item -> item.id())
                        .toList());
    }

    @Test
    void confirmingServiceSaleKeepsItAvailable() {
        listing.setType(ListingType.SERVICE);
        listings.saveAndFlush(listing);

        Long conversationId = service.startConversation(listing.getId());
        service.sendMessage(conversationId, "Thanks for the service.");

        login(provider);
        sales.confirmSaleAndClose(conversationId);

        var savedListing = listings.findById(listing.getId()).orElseThrow();
        assertEquals(ListingStatus.ACTIVE, savedListing.getStatus());

        login(outsider);

        var available = catalog.search(
                new com.mavworks.dto.SearchFilter("", null), null);

        assertEquals(
                List.of(listing.getId()),
                available.stream().map(item -> item.id()).toList());

        Long newConversationId = service.startConversation(listing.getId());
        assertNotEquals(conversationId, newConversationId);
    }

    @Test
    void soldProductCannotBeConfirmedForAnotherBuyer() {
        Long firstConversationId = service.startConversation(listing.getId());
        service.sendMessage(firstConversationId, "I will buy it.");

        login(outsider);
        Long secondConversationId = service.startConversation(listing.getId());
        service.sendMessage(secondConversationId, "Is it available?");

        login(provider);
        sales.confirmSaleAndClose(firstConversationId);

        assertThrows(IllegalArgumentException.class,
                () -> sales.confirmSaleAndClose(secondConversationId));

        var secondConversation = conversations.findById(secondConversationId)
                .orElseThrow();

        assertNull(secondConversation.getSaleConfirmedAt());
        assertNull(secondConversation.getClosedAt());

        login(outsider);
        assertTrue(reviews.getReviewableListings(provider.getId()).isEmpty());

        // The unsuccessful buyer can still read their conversation.
        assertEquals(1, service.getChat(secondConversationId, null).messages().size());
    }

    @Test
    void concurrentProductSalesAllowOnlyOneBuyer() throws Exception {
        Long firstId = service.startConversation(listing.getId());
        service.sendMessage(firstId, "I will buy it.");

        login(outsider);
        Long secondId = service.startConversation(listing.getId());
        service.sendMessage(secondId, "I will buy it too.");

        var start = new CountDownLatch(1);
        var ready = new CountDownLatch(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var attempts = new java.util.ArrayList<Future<Boolean>>();

            for (Long conversationId : List.of(firstId, secondId)) {
                attempts.add(executor.submit(() -> {
                    login(provider);
                    try {
                        ready.countDown();

                        if (!start.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Start timed out");
                        }

                        try {
                            sales.confirmSaleAndClose(conversationId);
                            return true;
                        } catch (IllegalArgumentException ex) {
                            assertEquals(
                                    "This listing has already been sold.",
                                    ex.getMessage());
                            return false;
                        }
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int successfulSales = 0;
            for (var attempt : attempts) {
                if (attempt.get(10, TimeUnit.SECONDS)) {
                    successfulSales++;
                }
            }

            assertEquals(1, successfulSales);
        }

        assertEquals(
                ListingStatus.SOLD,
                listings.findById(listing.getId()).orElseThrow().getStatus());

        var first = conversations.findById(firstId).orElseThrow();
        var second = conversations.findById(secondId).orElseThrow();

        int confirmedCount =
                (first.getSaleConfirmedAt() != null ? 1 : 0)
                        + (second.getSaleConfirmedAt() != null ? 1 : 0);

        int closedCount =
                (first.getClosedAt() != null ? 1 : 0)
                        + (second.getClosedAt() != null ? 1 : 0);

        assertEquals(1, confirmedCount);
        assertEquals(1, closedCount);
    }

    @Test
    void removedListingCannotHaveSaleConfirmed() {
        Long conversationId = service.startConversation(listing.getId());
        service.sendMessage(conversationId, "We agreed on the sale.");

        listing.setStatus(ListingStatus.REMOVED);
        listings.saveAndFlush(listing);

        login(provider);

        var error = assertThrows(
                IllegalArgumentException.class,
                () -> sales.confirmSaleAndClose(conversationId));

        assertEquals(
                "A removed listing cannot have a sale confirmed.",
                error.getMessage());

        var savedConversation = conversations.findById(conversationId)
                .orElseThrow();

        assertNull(savedConversation.getSaleConfirmedAt());
        assertNull(savedConversation.getClosedAt());

        assertEquals(
                ListingStatus.REMOVED,
                listings.findById(listing.getId()).orElseThrow().getStatus());
    }
    @Test
    void ownerCanEditListingWithoutChangingTypeStatusOrExpiration() {
        login(provider);
        var originalExpiration = listings.findById(listing.getId())
                .orElseThrow().getExpiresAt();

        management.updateDetails(
                listing.getId(),
                " Updated book ",
                " Revised description ",
                new BigDecimal("25.50"),
                ListingCategory.ART);

        var saved = listings.findById(listing.getId()).orElseThrow();
        assertEquals("Updated book", saved.getName());
        assertEquals("Revised description", saved.getDescription());
        assertEquals(new BigDecimal("25.50"), saved.getPrice());
        assertEquals(ListingCategory.ART, saved.getCategory());
        assertEquals(ListingType.PRODUCT, saved.getType());
        assertEquals(ListingStatus.ACTIVE, saved.getStatus());
        assertEquals(originalExpiration, saved.getExpiresAt());
    }

    @Test
    void invalidEditsLeaveListingUnchanged() {
        login(provider);

        for (String invalidPrice : List.of("-1", "1.001", "1000000")) {
            assertThrows(IllegalArgumentException.class,
                    () -> management.updateDetails(
                            listing.getId(), "Changed", "Changed",
                            new BigDecimal(invalidPrice), ListingCategory.ART));
        }

        for (String invalidName : List.of("", " ", "x".repeat(256))) {
            assertThrows(IllegalArgumentException.class,
                    () -> management.updateDetails(
                            listing.getId(), invalidName, "Valid description",
                            BigDecimal.TEN, ListingCategory.OTHER));
        }

        for (String invalidDescription : List.of("", " ", "x".repeat(4001))) {
            assertThrows(IllegalArgumentException.class,
                    () -> management.updateDetails(
                            listing.getId(), "Valid name", invalidDescription,
                            BigDecimal.TEN, ListingCategory.OTHER));
        }

        assertThrows(IllegalArgumentException.class,
                () -> management.updateDetails(
                        listing.getId(), "Changed", "Changed",
                        null, ListingCategory.OTHER));

        var saved = listings.findById(listing.getId()).orElseThrow();
        assertEquals("Book", saved.getName());
        assertEquals("A textbook", saved.getDescription());
        assertEquals(0, BigDecimal.TEN.compareTo(saved.getPrice()));
        assertEquals(ListingCategory.OTHER, saved.getCategory());
    }

    @Test
    void onlyOwnerCanManageListing() {
        // The setup signs in as the buyer, who does not own this listing.
        assertThrows(AccessDeniedException.class,
                () -> management.updateDetails(
                        listing.getId(), "Changed", "Changed",
                        BigDecimal.ONE, ListingCategory.ART));
        assertThrows(AccessDeniedException.class,
                () -> management.withdraw(listing.getId()));
        assertThrows(AccessDeniedException.class,
                () -> management.renew(listing.getId()));

        SecurityContextHolder.clearContext();

        assertThrows(AccessDeniedException.class,
                () -> management.updateDetails(
                        listing.getId(), "Changed", "Changed",
                        BigDecimal.ONE, ListingCategory.ART));
        assertThrows(AccessDeniedException.class,
                () -> management.withdraw(listing.getId()));
        assertThrows(AccessDeniedException.class,
                () -> management.renew(listing.getId()));

        var saved = listings.findById(listing.getId()).orElseThrow();
        assertEquals("Book", saved.getName());
        assertEquals(ListingStatus.ACTIVE, saved.getStatus());
    }

    @Test
    void withdrawalHidesListingButPreservesDashboardAndConversation() {
        Long conversationId = service.startConversation(listing.getId());
        service.sendMessage(conversationId, "Is this available?");

        login(provider);
        management.withdraw(listing.getId());

        var ownedListing = dashboard.load().listings().getFirst();
        assertEquals(listing.getId(), ownedListing.id());
        assertEquals(ListingStatus.WITHDRAWN, ownedListing.status());
        assertNotNull(ownedListing.expiresAt());

        login(outsider);
        assertTrue(catalog.search(
                new com.mavworks.dto.SearchFilter("", null), null).isEmpty());
        assertTrue(reviews.getProviderProfile(provider.getId())
                .orElseThrow().listings().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> service.startConversation(listing.getId()));

        login(buyer);
        assertEquals(conversationId, service.startConversation(listing.getId()));
        assertEquals(1, service.getChat(conversationId, null).messages().size());
        service.sendMessage(conversationId, "Following up on our conversation.");
        assertEquals(2, service.getChat(conversationId, null).messages().size());
    }

    @Test
    void withdrawnListingCanBeEditedAndRenewed() {
        login(provider);
        management.withdraw(listing.getId());

        management.updateDetails(
                listing.getId(), "Updated book", "Updated description",
                BigDecimal.TEN, ListingCategory.OTHER);

        assertEquals(ListingStatus.WITHDRAWN,
                listings.findById(listing.getId()).orElseThrow().getStatus());

        var before = java.time.Instant.now();
        management.renew(listing.getId());
        var after = java.time.Instant.now();

        var saved = listings.findById(listing.getId()).orElseThrow();
        var thirtyDays = java.time.Duration.ofDays(30);
        var tolerance = java.time.Duration.ofMillis(1);
        assertEquals(ListingStatus.ACTIVE, saved.getStatus());
        assertFalse(saved.getExpiresAt().isBefore(before.plus(thirtyDays).minus(tolerance)));
        assertFalse(saved.getExpiresAt().isAfter(after.plus(thirtyDays).plus(tolerance)));

        login(buyer);
        assertEquals(listing.getId(), catalog.search(
                new com.mavworks.dto.SearchFilter("", null), null).getFirst().id());
        assertEquals(listing.getId(), reviews.getProviderProfile(provider.getId())
                .orElseThrow().listings().getFirst().id());
    }

    @Test
    void soldAndRemovedListingsCannotBeEditedWithdrawnOrRenewed() {
        login(provider);

        for (ListingStatus status :
                List.of(ListingStatus.SOLD, ListingStatus.REMOVED)) {
            var existing = listings.findById(listing.getId()).orElseThrow();
            existing.setStatus(status);
            listings.saveAndFlush(existing);

            assertThrows(IllegalArgumentException.class,
                    () -> management.updateDetails(
                            listing.getId(), "Changed", "Changed",
                            BigDecimal.ONE, ListingCategory.ART));
            assertThrows(IllegalArgumentException.class,
                    () -> management.withdraw(listing.getId()));
            assertThrows(IllegalArgumentException.class,
                    () -> management.renew(listing.getId()));

            var saved = listings.findById(listing.getId()).orElseThrow();
            assertEquals(status, saved.getStatus());
            assertEquals("Book", saved.getName());
        }
    }

    @Test
    void existingSaleCanBeCompletedAfterWithdrawal() {
        Long conversationId = service.startConversation(listing.getId());
        service.sendMessage(conversationId, "I'll take it.");

        login(provider);
        management.withdraw(listing.getId());
        sales.confirmSaleAndClose(conversationId);

        assertEquals(ListingStatus.SOLD,
                listings.findById(listing.getId()).orElseThrow().getStatus());
        assertNotNull(conversations.findById(conversationId)
                .orElseThrow().getSaleConfirmedAt());
    }

    private User account(String name) {
        User user = new User();
        user.setUsername(name);
        user.setEmail(name + "@mavs.uta.edu");
        user.setHashedPassword("test-only");
        return users.saveAndFlush(user);
    }
    private void login(User user) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user.getUsername(), null, List.of()));
        SecurityContextHolder.setContext(context);
    }
}

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

package com.mavworks;

import com.mavworks.model.*;
import com.mavworks.repository.*;
import com.mavworks.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
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
        "spring.datasource.url=jdbc:h2:mem:bookings-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.default_schema=mavworks",
        "spring.flyway.enabled=true", "spring.flyway.locations=classpath:db/migration/postgresql",
        "spring.flyway.default-schema=mavworks", "spring.flyway.schemas=mavworks", "spring.flyway.create-schemas=true"
})
@Transactional
class BookingWorkflowTest {
    @Autowired UserRepository users;
    @Autowired ListingRepository listings;
    @Autowired BookingRepository bookings;
    @Autowired ConversationRepository conversations;
    @Autowired MessageRepository messages;
    @Autowired AvailabilityRepository slots;
    @Autowired BookingService service;
    @Autowired AvailabilityService availability;
    private User buyer, provider, stranger;
    private Listing listing;
    private Conversation chat;
    private LocalDate date;

    @BeforeEach void setUp() {
        buyer = account("booking-buyer"); provider = account("booking-provider"); stranger = account("booking-stranger");
        listing = listing("Tutoring", ListingType.SERVICE);
        chat = conversations.saveAndFlush(new Conversation(buyer, listing));
        date = LocalDate.now(BookingService.BOOKING_ZONE).plusDays(2);
        login(buyer);
    }
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void availabilityTogglesAndIsPrivate() {
        availability.setAvailable(date, 0, true);
        availability.setAvailable(date, 47, true);
        availability.setAvailable(date, 47, true);
        assertEquals(Set.of(0, 47), availability.day(date));
        assertEquals(Set.of(0, 47), availability.month(YearMonth.from(date)).get(date));
        availability.setAvailable(date, 0, false);
        assertEquals(Set.of(47), availability.day(date));
        login(stranger);
        assertTrue(availability.day(date).isEmpty());
    }
    @Test void requestedSlotsDetermineDurationAndBothCalendarsShowConfirmation() {
        Long id = request(chat, Set.of(24, 25, 26));
        Booking saved = bookings.findById(id).orElseThrow();
        assertEquals(BookingStatus.REQUESTED, saved.getStatus());
        assertEquals(Duration.ofMinutes(90), Duration.between(saved.getStartAt(), saved.getEndAt()));
        assertFalse(service.getConversationBookings(chat.getId()).getFirst().canRespond());
        login(provider);
        assertTrue(service.getConversationBookings(chat.getId()).getFirst().canRespond());
        service.approve(id);
        assertEquals(BookingStatus.CONFIRMED, service.getMyBookings().getFirst().status());
        login(buyer);
        assertEquals(BookingStatus.CONFIRMED, service.getMyBookings().getFirst().status());
    }
    @Test void providerCanRequestProductPickupAndBuyerCanAccept() {
        Listing product = listing("Textbook", ListingType.PRODUCT);
        Conversation productChat = conversations.saveAndFlush(new Conversation(buyer, product));
        login(provider);
        Long id = request(productChat, Set.of(47));
        assertEquals(date.plusDays(1).atStartOfDay(BookingService.BOOKING_ZONE).toInstant(), bookings.findById(id).orElseThrow().getEndAt());
        login(buyer);
        service.approve(id);
        assertEquals(BookingStatus.CONFIRMED, bookings.findById(id).orElseThrow().getStatus());
    }
    @Test void buyerCanRequestProductPickupFromListingAndSellerCanAccept() {
        Listing product = listing("Textbook", ListingType.PRODUCT);
        Conversation productChat = conversations.saveAndFlush(new Conversation(buyer, product));
        Long conversationId = service.conversationForListing(product.getId());
        assertEquals(productChat.getId(), conversationId);
        assertEquals(product.getId(), service.listingForConversation(conversationId));
        Long id = service.requestAppointment(conversationId, date, Set.of(24), Map.of(date, Map.of(24, true)));
        Booking appointment = bookings.findById(id).orElseThrow();
        assertEquals(BookingStatus.REQUESTED, appointment.getStatus());
        assertEquals(Duration.ofMinutes(30), Duration.between(appointment.getStartAt(), appointment.getEndAt()));
        login(provider);
        service.approve(id);
        assertEquals(BookingStatus.CONFIRMED, service.getConversationBookings(conversationId).getFirst().status());
    }
    @Test void declineDeletesRequest() {
        Long id = request(chat, Set.of(24));
        login(provider); service.decline(id);
        assertFalse(bookings.existsById(id));
    }
    @Test void nonParticipantsAndRequesterCannotRespond() {
        Long id = request(chat, Set.of(24));
        assertThrows(AccessDeniedException.class, () -> service.approve(id));
        login(stranger);
        assertTrue(service.getMyBookings().isEmpty());
        assertThrows(AccessDeniedException.class, () -> service.getConversationBookings(chat.getId()));
        assertThrows(AccessDeniedException.class, () -> service.requestAppointment(chat.getId(), date, Set.of(24)));
        assertThrows(AccessDeniedException.class, () -> service.approve(id));
        assertThrows(AccessDeniedException.class, () -> service.decline(id));
    }
    @Test void rejectsMissingAvailabilityGapsAndClosedConversations() {
        assertThrows(IllegalArgumentException.class, () -> service.requestAppointment(chat.getId(), date, Set.of(24)));
        assertThrows(IllegalArgumentException.class, () -> request(chat, Set.of(24, 26)));
        chat.confirmSale(provider);
        assertThrows(IllegalArgumentException.class, () -> request(chat, Set.of(24)));
    }
    @Test void preventsConflictsAcrossDifferentListingsForEitherParticipant() {
        Long first = request(chat, Set.of(24, 25));
        Listing secondListing = listing("Another service", ListingType.SERVICE);
        Conversation other = conversations.saveAndFlush(new Conversation(buyer, secondListing));
        Long second = request(other, Set.of(25, 26));
        login(provider); service.approve(first);
        assertThrows(IllegalArgumentException.class, () -> service.approve(second));
        assertEquals(BookingStatus.REQUESTED, bookings.findById(second).orElseThrow().getStatus());
    }
    @Test void expiresAtStartOrAfter48Hours() {
        Long id = request(chat, Set.of(24));
        Booking booking = bookings.findById(id).orElseThrow();
        org.springframework.test.util.ReflectionTestUtils.setField(booking, "createdAt", Instant.now().minus(Duration.ofHours(49)));
        service.advanceLifecycle(id);
        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
        Booking past = bookings.saveAndFlush(new Booking(chat, buyer, Instant.now().minusSeconds(60), Instant.now().plusSeconds(900)));
        service.advanceLifecycle(past.getId());
        assertEquals(BookingStatus.EXPIRED, past.getStatus());
    }
    @Test void confirmedAppointmentMarksListingSoldAndNotifiesConversationOnce() {
        Booking past = new Booking(chat, buyer, Instant.now().minusSeconds(1800), Instant.now().minusSeconds(900));
        past.approve(); bookings.saveAndFlush(past);
        service.advanceLifecycle(past.getId());
        service.advanceLifecycle(past.getId());
        assertEquals(BookingStatus.COMPLETED, past.getStatus());
        assertEquals(ListingStatus.SOLD, listing.getStatus());
        assertNotNull(chat.getSaleConfirmedAt());
        assertEquals(1, messages.count());
        assertTrue(messages.findAll().getFirst().getBody().contains("automatically been marked Sold"));
    }
    @Test void savesDraftAvailabilityTogetherWithAppointment() {
        Long id = service.requestAppointment(chat.getId(), date, Set.of(24, 25), Map.of(date, Map.of(24, true, 25, true)));
        assertNotNull(id);
        assertEquals(Set.of(24, 25), availability.day(date));
    }
    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void failedRequestRollsBackDraftAvailabilityAndCanBeRetried() {
        try {
            assertThrows(IllegalArgumentException.class, () -> service.requestAppointment(chat.getId(), date,
                    Set.of(24, 26), Map.of(date, Map.of(24, true, 26, true))));
            assertTrue(availability.day(date).isEmpty());
            assertTrue(service.getConversationBookings(chat.getId()).isEmpty());
            assertNotNull(service.requestAppointment(chat.getId(), date,
                    Set.of(24), Map.of(date, Map.of(24, true))));
        } finally {
            bookings.deleteAll(); conversations.deleteAll(); slots.deleteAll(); listings.deleteAll(); users.deleteAll();
        }
    }
    @Test void rejectsInvalidHalfHoursPastDatesAndClockChanges() {
        assertThrows(IllegalArgumentException.class, () -> availability.setAvailable(date, 48, true));
        assertThrows(IllegalArgumentException.class, () -> availability.setAvailable(date.minusYears(1), 0, true));
        assertThrows(IllegalArgumentException.class, () -> BookingService.toInstant(LocalDateTime.of(2027, 3, 14, 2, 15)));
        assertThrows(IllegalArgumentException.class, () -> BookingService.toInstant(LocalDateTime.of(2027, 11, 7, 1, 15)));
    }
    private Long request(Conversation conversation, Set<Integer> selected) {
        selected.forEach(slot -> availability.setAvailable(date, slot, true));
        return service.requestAppointment(conversation.getId(), date, selected);
    }
    private User account(String username) {
        var user = new User(); user.setUsername(username); user.setEmail(username + "@mavs.uta.edu"); user.setHashedPassword("test-only");
        return users.saveAndFlush(user);
    }
    private Listing listing(String name, ListingType type) {
        var result = new Listing(); result.setOwner(provider); result.setName(name); result.setDescription("A listing");
        result.setPrice(BigDecimal.TEN); result.setType(type); return listings.saveAndFlush(result);
    }
    private void login(User user) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user.getUsername(), null, List.of()));
        SecurityContextHolder.setContext(context);
    }
}

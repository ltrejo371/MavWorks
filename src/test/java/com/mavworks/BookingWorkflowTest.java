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
    @Autowired MessagingService messaging;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
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
    @Test void incomingRequestChecksAllRecipientSlotsWithoutSharingTheirAvailability() {
        Long id = request(chat, Set.of(24, 25));
        assertNull(service.getAppointment(id).availableForRecipient());
        login(provider);
        assertEquals(Boolean.FALSE, service.getAppointment(id).availableForRecipient());
        availability.setAvailable(date, 24, true);
        assertEquals(Boolean.FALSE, service.getAppointment(id).availableForRecipient());
        availability.setAvailable(date, 25, true);
        assertEquals(Boolean.TRUE, service.getAppointment(id).availableForRecipient());
        availability.setAvailable(date, 25, false);
        assertEquals(Boolean.FALSE, service.getConversationBookings(chat.getId()).getFirst().availableForRecipient());
        service.approve(id); // Missing availability is a warning, not a prohibition.
        assertEquals(BookingStatus.CONFIRMED, service.getAppointment(id).status());
        assertNull(service.getAppointment(id).availableForRecipient());
    }
    @Test void providerRequestsAndReschedulesCheckBuyersAvailability() {
        login(provider);
        Long id = request(chat, Set.of(47));
        login(buyer);
        availability.setAvailable(date, 47, true);
        assertEquals(Boolean.TRUE, service.getAppointment(id).availableForRecipient());
        service.approve(id);
        login(provider);
        service.reschedule(id, date.plusDays(1), Set.of(24), Map.of(date.plusDays(1), Map.of(24, true)));
        assertNull(service.getAppointment(id).availableForRecipient());
        login(buyer);
        assertEquals(Boolean.FALSE, service.getAppointment(id).availableForRecipient());
    }
    @Test void legacyRequestsCheckPartialSlotsAcrossMidnight() {
        Booking request = bookings.saveAndFlush(new Booking(chat, buyer,
                BookingService.toInstant(date.atTime(23, 45)), BookingService.toInstant(date.plusDays(1).atTime(0, 15))));
        login(provider);
        availability.setAvailable(date, 47, true);
        assertEquals(Boolean.FALSE, service.getAppointment(request.getId()).availableForRecipient());
        availability.setAvailable(date.plusDays(1), 0, true);
        assertEquals(Boolean.TRUE, service.getAppointment(request.getId()).availableForRecipient());
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
    @Test
    void completedProductAppointmentMarksListingSold() {
        Listing product = listing("Textbook", ListingType.PRODUCT);
        Conversation productChat =
                conversations.saveAndFlush(new Conversation(buyer, product));

        Booking appointment = completedAppointment(productChat);

        assertEquals(BookingStatus.COMPLETED, appointment.getStatus());
        assertEquals(ListingStatus.SOLD, product.getStatus());
        assertNotNull(productChat.getSaleConfirmedAt());
        assertNotNull(productChat.getClosedAt());
        assertEquals(1, messages.count());
        assertTrue(messages.findAll().getFirst().getBody()
                .contains("automatically been marked Sold"));
    }

    @Test
    void completedServiceAppointmentAllowsSameBuyerToBookAgain() {
        Booking appointment = completedAppointment(chat);

        assertEquals(BookingStatus.COMPLETED, appointment.getStatus());
        assertEquals(ListingStatus.ACTIVE, listing.getStatus());
        assertNotNull(chat.getSaleConfirmedAt());
        assertNotNull(chat.getClosedAt());
        assertEquals(1, messages.count());
        assertEquals(
                "Service appointment completed. The service listing's status has not changed.",
                messages.findAll().getFirst().getBody());

        login(buyer);
        Long nextConversationId = messaging.startConversation(listing.getId());

        assertNotEquals(chat.getId(), nextConversationId);
        assertEquals(nextConversationId,
                service.conversationForListing(listing.getId()));
        assertTrue(service.canRequestFromListing(listing.getId()));

        Conversation nextChat =
                conversations.findById(nextConversationId).orElseThrow();
        Long nextAppointmentId = request(nextChat, Set.of(24));

        login(provider);
        service.approve(nextAppointmentId);

        assertEquals(BookingStatus.CONFIRMED,
                bookings.findById(nextAppointmentId).orElseThrow().getStatus());
        assertEquals(BookingStatus.COMPLETED, appointment.getStatus());
    }

    @Test
    void completedServiceAppointmentPreservesWithdrawnStatus() {
        listing.setStatus(ListingStatus.WITHDRAWN);
        listings.saveAndFlush(listing);
        Booking appointment = completedAppointment(chat);

        assertEquals(BookingStatus.COMPLETED, appointment.getStatus());
        assertEquals(ListingStatus.WITHDRAWN, listing.getStatus());
        assertEquals(1, messages.count());
    }

    @Test void rescheduleRequiresTheOtherParticipantToConfirmInEitherDirection() {
        Long id = request(chat, Set.of(24, 25));
        login(provider); service.approve(id);
        org.springframework.test.util.ReflectionTestUtils.setField(bookings.findById(id).orElseThrow(),
                "createdAt", Instant.now().minus(Duration.ofDays(3)));
        bookings.flush();
        service.reschedule(id, date, Set.of(25, 26), Map.of(date, Map.of(25, true, 26, true)));
        entityManager.clear();
        Instant expected = BookingService.toInstant(date.atTime(12, 30));
        assertEquals(expected, service.getAppointment(id).startAt());
        assertEquals(BookingStatus.REQUESTED, service.getAppointment(id).status());
        assertFalse(service.getAppointment(id).canRespond());
        assertFalse(bookings.findById(id).orElseThrow().isExpired(Instant.now()));
        assertThrows(AccessDeniedException.class, () -> service.approve(id));
        assertThrows(AccessDeniedException.class, () -> service.decline(id));
        login(buyer);
        assertEquals(expected, service.getMyBookings().getFirst().startAt());
        assertTrue(service.getAppointment(id).canRespond());
        service.approve(id);
        assertEquals(BookingStatus.CONFIRMED, service.getAppointment(id).status());
        service.reschedule(id, date.plusDays(1), Set.of(24), Map.of(date.plusDays(1), Map.of(24, true)));
        assertEquals(BookingService.toInstant(date.plusDays(1).atTime(12, 0)), service.getAppointment(id).startAt());
        assertEquals(BookingStatus.REQUESTED, service.getAppointment(id).status());
        assertFalse(service.getAppointment(id).canRespond());
        assertThrows(AccessDeniedException.class, () -> service.approve(id));
        login(provider);
        assertTrue(service.getAppointment(id).canRespond());
        service.approve(id);
        assertEquals(BookingStatus.CONFIRMED, service.getAppointment(id).status());
        assertEquals(2, messages.count());
        assertTrue(messages.findAll().stream().allMatch(m -> m.getBody().contains("Appointment reschedule requested")));
    }
    @Test void rescheduleReleasesOldReservationAndRechecksConflictsOnAcceptance() {
        Long id = request(chat, Set.of(24));
        login(provider); service.approve(id); login(buyer);
        service.reschedule(id, date, Set.of(26), Map.of(date, Map.of(26, true)));
        Long oldTime = request(chat, Set.of(24));
        Long proposedTime = request(chat, Set.of(26));
        login(provider);
        service.approve(oldTime);
        service.approve(proposedTime);
        assertThrows(IllegalArgumentException.class, () -> service.approve(id));
        assertEquals(BookingStatus.REQUESTED, service.getAppointment(id).status());
        service.decline(id);
        assertFalse(bookings.existsById(id));
    }
    @Test void cancellationDeletesAppointmentAndNotifiesConversation() {
        Long id = request(chat, Set.of(24));
        login(provider); service.approve(id);
        login(buyer); service.cancelAppointment(id);
        assertFalse(bookings.existsById(id));
        assertTrue(service.getMyBookings().isEmpty());
        login(provider); assertTrue(service.getMyBookings().isEmpty());
        assertEquals(1, messages.count());
        assertTrue(messages.findAll().getFirst().getBody().contains("Appointment cancelled"));
    }
    @Test void strangersCannotReadRescheduleOrCancelAppointments() {
        Long id = request(chat, Set.of(24));
        login(provider); service.approve(id); login(stranger);
        assertThrows(AccessDeniedException.class, () -> service.getAppointment(id));
        assertThrows(AccessDeniedException.class, () -> service.reschedule(id, date, Set.of(26), Map.of()));
        assertThrows(AccessDeniedException.class, () -> service.cancelAppointment(id));
    }
    @Test void rescheduleRejectsOtherConfirmedAppointments() {
        Long first = request(chat, Set.of(24));
        Long second = request(chat, Set.of(26));
        login(provider); service.approve(first); service.approve(second); login(buyer);
        assertThrows(IllegalArgumentException.class, () -> service.reschedule(first, date, Set.of(26), Map.of()));
        assertEquals(BookingService.toInstant(date.atTime(12, 0)), service.getAppointment(first).startAt());
        assertEquals(0, messages.count());
    }
    @Test void requestedAppointmentsCannotBeRescheduledOrCancelledAsConfirmed() {
        Long id = request(chat, Set.of(24));
        assertThrows(IllegalArgumentException.class, () -> service.cancelAppointment(id));
        assertThrows(IllegalArgumentException.class, () -> service.reschedule(id, date, Set.of(24), Map.of()));
    }
    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void failedRescheduleRollsBackAvailabilityTimesAndNotification() {
        try {
            Long id = request(chat, Set.of(24));
            login(provider); service.approve(id); login(buyer);
            Instant original = service.getAppointment(id).startAt();
            assertThrows(IllegalArgumentException.class, () -> service.reschedule(id, date.plusDays(1),
                    Set.of(24, 26), Map.of(date.plusDays(1), Map.of(24, true, 26, true))));
            assertEquals(original, service.getAppointment(id).startAt());
            assertEquals(BookingStatus.CONFIRMED, service.getAppointment(id).status());
            assertTrue(availability.day(date.plusDays(1)).isEmpty());
            assertEquals(0, messages.count());
        } finally {
            messages.deleteAll(); bookings.deleteAll(); conversations.deleteAll(); slots.deleteAll(); listings.deleteAll(); users.deleteAll();
        }
    }

    private Booking completedAppointment(Conversation conversation) {
        Instant now = Instant.now();
        Booking appointment = new Booking(
                conversation,
                buyer,
                now.minusSeconds(1800),
                now.minusSeconds(900));

        appointment.approve();
        bookings.saveAndFlush(appointment);

        service.advanceLifecycle(appointment.getId());
        service.advanceLifecycle(appointment.getId());

        return appointment;
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

    @Test
    void bulkAvailabilityChangesOnlySelectedDaysAndTimes() {
        LocalDate monday = date.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        Set<DayOfWeek> weekdays = Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);

        // An existing selection outside the edited range must survive.
        availability.setAvailable(monday, 30, true);
        assertEquals(4, availability.setAvailableRange(monday, monday.plusDays(13), weekdays, 18, 20, true));
        // Applying the same selection again must not create duplicate records.
        availability.setAvailableRange(monday, monday.plusDays(13), weekdays, 18, 20, true);
        assertEquals(Set.of(18, 19, 30), availability.day(monday));
        assertEquals(Set.of(18, 19), availability.day(monday.plusDays(2)));
        assertEquals(Set.of(18, 19), availability.day(monday.plusDays(7)));
        assertEquals(Set.of(18, 19), availability.day(monday.plusDays(9)));
        assertTrue(availability.day(monday.plusDays(1)).isEmpty());
        assertTrue(availability.day(monday.plusDays(14)).isEmpty());
        assertEquals(3, slots.findByUserIdAndDate(buyer.getId(), monday).size());
        availability.setAvailableRange(monday, monday.plusDays(13), weekdays, 19, 20, false);
        assertEquals(Set.of(18, 30), availability.day(monday));
        assertEquals(Set.of(18), availability.day(monday.plusDays(2)));
        assertEquals(Set.of(18), availability.day(monday.plusDays(7)));
        assertEquals(Set.of(18), availability.day(monday.plusDays(9)));
    }

    @Test
    void bulkAvailabilityIsPrivateAndSupportsEndOfDay() {
        LocalDate monday = date.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        availability.setAvailableRange(monday, monday, Set.of(DayOfWeek.MONDAY), 0, 48, true);assertEquals(48, availability.day(monday).size());
        login(stranger);
        assertTrue(availability.day(monday).isEmpty());
        availability.setAvailableRange(monday, monday, Set.of(DayOfWeek.MONDAY), 0, 48, false);
        login(buyer);
        assertEquals(48, availability.day(monday).size());
    }

    @Test
    void changingAvailabilityDoesNotCancelConfirmedAppointments() {
        Long appointmentId = request(chat, Set.of(24, 25));
        login(provider);
        service.approve(appointmentId);

        login(buyer);
        availability.setAvailableRange(date, date, Set.of(date.getDayOfWeek()), 24, 26, false);
        assertTrue(availability.day(date).isEmpty());
        assertEquals(BookingStatus.CONFIRMED, bookings.findById(appointmentId).orElseThrow().getStatus());

        // Another overlapping appointment must still be rejected.
        availability.setAvailableRange(date, date, Set.of(date.getDayOfWeek()), 24, 26, true);
        assertThrows(IllegalArgumentException.class, () -> request(chat, Set.of(24, 25)));
    }

    @Test
    void bulkAvailabilityRejectsInvalidSelectionsWithoutSaving() {
        Set<DayOfWeek> weekdays = Set.of(date.getDayOfWeek());
        assertThrows(IllegalArgumentException.class, () -> availability.setAvailableRange(null, date, weekdays, 18, 20, true));
        assertThrows(IllegalArgumentException.class, () -> availability.setAvailableRange(date, date.minusDays(1), weekdays, 18, 20, true));
        assertThrows(IllegalArgumentException.class, () -> availability.setAvailableRange(date.minusYears(1), date, weekdays, 18, 20, true));
        assertThrows(IllegalArgumentException.class, () -> availability.setAvailableRange(date, date.plusDays(90), weekdays, 18, 20, true));
        assertThrows(IllegalArgumentException.class, () -> availability.setAvailableRange(date, date, Set.of(), 18, 20, true));
        assertThrows(IllegalArgumentException.class, () -> availability.setAvailableRange(date, date, Set.of(date.plusDays(1).getDayOfWeek()), 18, 20, true));
        assertThrows(IllegalArgumentException.class, () -> availability.setAvailableRange(date, date, weekdays, 20, 18, true));
        assertThrows(IllegalArgumentException.class, () -> availability.setAvailableRange(date, date, weekdays, -1, 20, true));
        assertThrows(IllegalArgumentException.class, () -> availability.setAvailableRange(date, date, weekdays, 18, 49, true));
        assertTrue(availability.day(date).isEmpty());
    }

    @Test
    void bulkAvailabilityRejectsClockChangeBeforeSavingAnyDates() {
        int year = LocalDate.now(BookingService.BOOKING_ZONE).getYear() + 1;
        LocalDate clockChange = LocalDate.of(year, Month.MARCH, 1).with(java.time.temporal.TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.SUNDAY));
        LocalDate previousSunday = clockChange.minusWeeks(1);
        // 2:00–3:00 AM includes nonexistent times on the spring clock change.
        assertThrows(IllegalArgumentException.class, () -> availability.setAvailableRange(previousSunday, clockChange, Set.of(DayOfWeek.SUNDAY), 4, 6, true));
        assertTrue(availability.day(previousSunday).isEmpty());
        assertTrue(availability.day(clockChange).isEmpty());
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

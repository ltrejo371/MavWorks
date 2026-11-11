package com.mavworks;

import com.mavworks.listing.ListingDetailsView;
import com.mavworks.model.*;
import com.mavworks.repository.*;
import com.mavworks.security.CurrentUser;
import com.mavworks.service.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.router.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListingAppointmentAccessTest {
    private final CurrentUser current = mock(CurrentUser.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final UserBlockRepository blocks = mock(UserBlockRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private BookingService service;
    private User buyer, seller;
    private Listing listing;
    private Conversation conversation;

    @BeforeEach void setup() {
        buyer = new User(); buyer.setUsername("buyer"); ReflectionTestUtils.setField(buyer, "id", 1L);
        seller = new User(); seller.setUsername("seller"); ReflectionTestUtils.setField(seller, "id", 2L);
        listing = new Listing(); listing.setOwner(seller); listing.setName("Listing");
        listing.setPrice(BigDecimal.TEN); listing.setDescription("Test listing"); listing.setType(ListingType.PRODUCT);
        ReflectionTestUtils.setField(listing, "id", 10L);
        conversation = new Conversation(buyer, listing); ReflectionTestUtils.setField(conversation, "id", 20L);
        when(current.username()).thenReturn(Optional.of("buyer"));
        when(users.findByUsername("buyer")).thenReturn(Optional.of(buyer));
        service = new BookingService(current, users, mock(ListingRepository.class), bookings,
                conversations, mock(AvailabilityRepository.class), blocks, mock(MessageRepository.class));
    }
    @Test void requiresAnExistingConversationForThisUserAndThisListing() {
        assertFalse(service.canRequestFromListing(10L));
        assertThrows(IllegalArgumentException.class, () -> service.conversationForListing(10L));
        when(conversations.findFirstByBuyerIdAndListingIdOrderByIdDesc(1L, 10L)).thenReturn(Optional.of(conversation));
        assertTrue(service.canRequestFromListing(10L));
        assertEquals(20L, service.conversationForListing(10L));
        assertFalse(service.canRequestFromListing(11L));
        verify(conversations, never()).save(any());
    }
    @Test void directAppointmentRequestCannotCreateItsOwnConversation() {
        assertThrows(IllegalArgumentException.class, () -> service.requestAppointment(99L,
                LocalDate.now(BookingService.BOOKING_ZONE).plusDays(1), Set.of(24)));
        verify(bookings, never()).saveAndFlush(any());
        verify(conversations, never()).save(any());
    }
    @Test void closedOrSoldConversationsDoNotQualify() {
        when(conversations.findFirstByBuyerIdAndListingIdOrderByIdDesc(1L, 10L)).thenReturn(Optional.of(conversation));
        conversation.confirmSale(seller);
        assertFalse(service.canRequestFromListing(10L));
        conversation.closeConversation();
        assertFalse(service.canRequestFromListing(10L));
    }
    @Test void blocksInEitherDirectionHideAppointmentAction() {
        when(conversations.findFirstByBuyerIdAndListingIdOrderByIdDesc(1L, 10L)).thenReturn(Optional.of(conversation));
        when(blocks.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);
        assertFalse(service.canRequestFromListing(10L));
        when(blocks.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(false);
        when(blocks.existsByBlockerIdAndBlockedId(2L, 1L)).thenReturn(true);
        assertFalse(service.canRequestFromListing(10L));
    }
    @Test void listingButtonAppearsOnlyAfterConversationExistsForProductsAndServices() {
        for (ListingType type : List.of(ListingType.PRODUCT, ListingType.SERVICE)) {
            listing.setType(type);
            var listingService = mock(ListingService.class);
            when(listingService.getListingDetails(10L)).thenReturn(Optional.of(listing));
            var eligibility = mock(BookingService.class);
            var view = new ListingDetailsView(listingService, mock(LocalImageStorageService.class),
                    mock(MessagingService.class), mock(BlockService.class), mock(ReportService.class), eligibility);
            var event = mock(BeforeEnterEvent.class);
            when(event.getRouteParameters()).thenReturn(new RouteParameters("listingId", "10"));
            view.beforeEnter(event);
            assertFalse(appointmentButton(view).isVisible());
            when(eligibility.canRequestFromListing(10L)).thenReturn(true);
            view.beforeEnter(event);
            assertTrue(appointmentButton(view).isVisible());
            listing.markSold(); view.beforeEnter(event);
            assertFalse(appointmentButton(view).isVisible());
            // The next iteration uses a fresh active listing fixture.
            setup();
        }
    }
    private Button appointmentButton(Component root) {
        return components(root).filter(c -> c instanceof Button b && b.getText().equals("Request Appointment"))
                .map(Button.class::cast).findFirst().orElseThrow();
    }
    private Stream<Component> components(Component root) { return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::components)); }
}

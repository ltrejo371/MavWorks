package com.mavworks.service;

import com.mavworks.model.*;
import com.mavworks.repository.*;
import com.mavworks.security.CurrentUser;
import java.time.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingService {
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    public static final ZoneId BOOKING_ZONE = ZoneId.of("America/Chicago");
    public record BookingSummary(Long id, Long listingId, Long conversationId, String listingName,
            String buyer, String provider, boolean canRespond, Instant startAt, Instant endAt, BookingStatus status) {}
    private final CurrentUser current;
    private final UserRepository users;
    private final ListingRepository listings;
    private final BookingRepository bookings;
    private final ConversationRepository conversations;
    private final AvailabilityRepository availability;
    private final UserBlockRepository blocks;
    private final MessageRepository messages;

    public BookingService(CurrentUser current, UserRepository users, ListingRepository listings,
            BookingRepository bookings, ConversationRepository conversations, AvailabilityRepository availability,
            UserBlockRepository blocks, MessageRepository messages) {
        this.current = current; this.users = users; this.listings = listings; this.bookings = bookings;
        this.conversations = conversations; this.availability = availability; this.blocks = blocks; this.messages = messages;
    }
    private User user() {
        return current.username().flatMap(users::findByUsername)
                .orElseThrow(() -> new AccessDeniedException("Sign in to manage appointments."));
    }
    public Long conversationForListing(Long listingId) {
        return requestableConversationForListing(listingId)
                .map(Conversation::getId).orElseThrow(() -> new IllegalArgumentException("Open a message conversation for this listing first."));
    }
    public boolean canRequestFromListing(Long listingId) {
        return requestableConversationForListing(listingId).isPresent();
    }
    private Optional<Conversation> requestableConversationForListing(Long listingId) {
        User viewer = user();
        if (listingId == null) return Optional.empty();
        return conversations.findFirstByBuyerIdAndListingIdOrderByIdDesc(viewer.getId(), listingId)
                .filter(chat -> chat.getClosedAt() == null && chat.getSaleConfirmedAt() == null
                        && chat.getListing().getStatus() == ListingStatus.ACTIVE)
                .filter(chat -> !blocks.existsByBlockerIdAndBlockedId(viewer.getId(), chat.getListing().getOwner().getId())
                        && !blocks.existsByBlockerIdAndBlockedId(chat.getListing().getOwner().getId(), viewer.getId()));
    }
    public Long listingForConversation(Long id) { return conversation(id, true).getListing().getId(); }
    private Conversation conversation(Long id, boolean requireOpen) {
        User viewer = user();
        Conversation chat = conversations.findById(id).orElseThrow(() -> new IllegalArgumentException("Conversation not found."));
        if (!chat.hasParticipant(viewer)) throw new AccessDeniedException("Conversation not available.");
        Long buyer = chat.getBuyer().getId(), provider = chat.getListing().getOwner().getId();
        if (blocks.existsByBlockerIdAndBlockedId(buyer, provider) || blocks.existsByBlockerIdAndBlockedId(provider, buyer))
            throw new AccessDeniedException("Conversation not available.");
        if (requireOpen && (chat.getClosedAt() != null || chat.getSaleConfirmedAt() != null
                || chat.getListing().getStatus() != ListingStatus.ACTIVE))
            throw new IllegalArgumentException("This listing conversation is no longer available for appointments.");
        return chat;
    }
    public List<BookingSummary> getMyBookings() {
        User viewer = user();
        return bookings.findForParticipant(viewer.getId()).stream().map(b -> summary(b, viewer)).toList();
    }
    public List<BookingSummary> getConversationBookings(Long id) {
        conversation(id, false);
        User viewer = user();
        return bookings.findByConversationIdOrderByStartAtDesc(id).stream().map(b -> summary(b, viewer)).toList();
    }
    private BookingSummary summary(Booking b, User viewer) {
        BookingStatus status = b.isExpired(Instant.now()) ? BookingStatus.EXPIRED : b.getStatus();
        return new BookingSummary(b.getId(), b.getListing().getId(), b.getConversation() == null ? null : b.getConversation().getId(),
                b.getListing().getName(), b.getBuyer().getUsername(), b.getListing().getOwner().getUsername(),
                !b.getRequester().getId().equals(viewer.getId()) && status == BookingStatus.REQUESTED
                        && b.getListing().getStatus() == ListingStatus.ACTIVE
                        && b.getConversation().getSaleConfirmedAt() == null && b.getConversation().getClosedAt() == null,
                b.getStartAt(), b.getEndAt(), status);
    }
    @Transactional
    public Long requestAppointment(Long conversationId, LocalDate date, Set<Integer> selected) {
        return requestAppointment(conversationId, date, selected, Map.of());
    }
    @Transactional
    public Long requestAppointment(Long conversationId, LocalDate date, Set<Integer> selected,
            Map<LocalDate, Map<Integer, Boolean>> edits) {
        Conversation chat = conversation(conversationId, true);
        User requester = user();
        lockParticipants(chat.getBuyer(), chat.getListing().getOwner());
        entityManager.refresh(chat, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(chat.getListing(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        conversation(conversationId, true);
        try {
            for (var day : edits.entrySet()) {
                Set<Integer> existing = new HashSet<>(availability.findByUserIdAndDate(requester.getId(), day.getKey())
                        .stream().map(Availability::getSlot).toList());
                for (var change : day.getValue().entrySet()) {
                    AvailabilityService.validate(day.getKey(), change.getKey());
                    if (change.getValue() && !existing.contains(change.getKey()))
                        availability.save(new Availability(requester, day.getKey(), change.getKey()));
                    else if (!change.getValue())
                        availability.deleteByUserIdAndDateAndSlot(requester.getId(), day.getKey(), change.getKey());
                }
            }
            availability.flush();
        } catch (org.springframework.dao.DataAccessException ex) { throw new AvailabilityService.SaveException(ex); }
        if (selected == null || selected.isEmpty()) throw new IllegalArgumentException("Select appointment times.");
        var sorted = new TreeSet<>(selected);
        if (sorted.last() - sorted.first() + 1 != sorted.size())
            throw new IllegalArgumentException("Appointment times have to be in consecutive slots.");
        sorted.forEach(slot -> AvailabilityService.validate(date, slot));
        var available = availability.findByUserIdAndDate(requester.getId(), date).stream().map(Availability::getSlot).toList();
        if (!available.containsAll(sorted)) throw new IllegalArgumentException("Mark the selected times available first.");
        Instant start = toInstant(date.atStartOfDay().plusMinutes(sorted.first() * (long) AvailabilityService.SLOT_MINUTES));
        Instant end = toInstant(date.atStartOfDay().plusMinutes((sorted.last() + 1) * (long) AvailabilityService.SLOT_MINUTES));
        if (!start.isAfter(Instant.now())) throw new IllegalArgumentException("Choose a future appointment time.");
        requireFree(chat.getBuyer().getId(), chat.getListing().getOwner().getId(), start, end);
        return bookings.saveAndFlush(new Booking(chat, requester, start, end)).getId();
    }
    private void lockParticipants(User first, User second) {
        List.of(first, second).stream().sorted(Comparator.comparing(User::getId))
                .forEach(u -> users.findForMessaging(u.getUsername()).orElseThrow());
    }
    private void requireFree(Long buyer, Long provider, Instant start, Instant end) {
        if (!bookings.findParticipantConflicts(buyer, provider, BookingStatus.CONFIRMED, start, end).isEmpty())
            throw new IllegalArgumentException("One of you already has a confirmed appointment at this time.");
    }
    @Transactional
    public void approve(Long id) {
        User viewer = user();
        Booking original = bookings.findById(id).orElseThrow(() -> new IllegalArgumentException("Appointment not found."));
        requireRecipient(original, viewer);
        lockParticipants(original.getBuyer(), original.getListing().getOwner());
        Booking b = bookings.findForUpdate(id).orElseThrow();
        entityManager.refresh(b);
        entityManager.refresh(b.getListing(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (b.getConversation() != null) conversation(b.getConversation().getId(), true);
        if (b.getListing().getStatus() != ListingStatus.ACTIVE) throw new IllegalArgumentException("Listing no longer available.");
        if (b.isExpired(Instant.now())) throw new IllegalArgumentException("This appointment request has expired.");
        requireFree(b.getBuyer().getId(), b.getListing().getOwner().getId(), b.getStartAt(), b.getEndAt());
        b.approve();
    }
    @Transactional
    public void decline(Long id) {
        Booking b = bookings.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Appointment not found."));
        requireRecipient(b, user());
        if (b.getConversation() != null) conversation(b.getConversation().getId(), true);
        if (b.getStatus() != BookingStatus.REQUESTED || b.isExpired(Instant.now()))
            throw new IllegalArgumentException("This request is no longer awaiting a response.");
        bookings.delete(b);
    }
    private void requireRecipient(Booking b, User viewer) {
        if ((!b.getBuyer().getId().equals(viewer.getId()) && !b.getListing().getOwner().getId().equals(viewer.getId()))
                || b.getRequester().getId().equals(viewer.getId()))
            throw new AccessDeniedException("Only the other participant can respond to this request.");
    }
    @Transactional
    public void advanceLifecycle(Long id) {
        Booking b = bookings.findForUpdate(id).orElse(null);
        if (b == null) return;
        Instant now = Instant.now();
        if (b.isExpired(now)) b.expire();
        if (b.getStatus() == BookingStatus.CONFIRMED && !b.getEndAt().isAfter(now)) {
            Conversation chat = b.getConversation();
            entityManager.refresh(chat, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            Listing listing = listings.findForManagement(b.getListing().getId()).orElseThrow();
            if (listing.getStatus() == ListingStatus.REMOVED) { b.cancel(); return; }
            boolean isProduct = listing.getType() == ListingType.PRODUCT;
            if (isProduct) {
                listing.markSold();
            }

            if (chat != null && chat.getClosedAt() == null) {
                String completionMessage = isProduct
                        ? "Appointment completed. The listing has automatically been marked Sold."
                        : "Service appointment completed. The service listing's status has not changed.";

                messages.save(listing.getOwner().sendMessage(chat, completionMessage));
                chat.confirmSale(listing.getOwner());
                chat.closeConversation();
            }
            b.complete();
        }
    }
    public static Instant toInstant(LocalDateTime localTime) {
        var offsets = BOOKING_ZONE.getRules().getValidOffsets(localTime);
        if (offsets.size() != 1) throw new IllegalArgumentException("This time is unavailable because of a daylight saving clock change.");
        return localTime.atOffset(offsets.getFirst()).toInstant();
    }
}

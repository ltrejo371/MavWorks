package com.mavworks.service;

import com.mavworks.model.*;
import com.mavworks.repository.*;
import com.mavworks.security.CurrentUser;
import java.time.*;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingService {
    public static final ZoneId BOOKING_ZONE = ZoneId.of("America/Chicago");

    public record ServiceOption(Long id, String name, String provider, int durationMinutes) {}
    public record AvailabilityOption(Long id, Instant startAt, Instant endAt) {}
    public record AvailabilitySummary(Long id, Long listingId, String listingName,
            Instant startAt, Instant endAt, boolean reserved) {}
    public record BookingSummary(Long id, Long listingId, String listingName,
            String buyer, String provider, boolean currentUserIsProvider,
            Instant startAt, Instant endAt, BookingStatus status, boolean buyerConfirmed) {}
    public record BusySlot(Instant startAt, Instant endAt) {}

    private final CurrentUser current;
    private final UserRepository users;
    private final ListingRepository listings;
    private final BookingRepository bookings;
    private final AvailabilitySlotRepository availability;

    public BookingService(CurrentUser current, UserRepository users, ListingRepository listings,
            BookingRepository bookings, AvailabilitySlotRepository availability) {
        this.current = current;
        this.users = users;
        this.listings = listings;
        this.bookings = bookings;
        this.availability = availability;
    }

    private User user() {
        return current.username().flatMap(users::findByUsername)
                .orElseThrow(() -> new AccessDeniedException("Sign in to manage bookings."));
    }

    public List<ServiceOption> availableServices() {
        User viewer = user();
        return listings.searchPublished(ListingStatus.ACTIVE, "", null, ListingType.SERVICE).stream()
                .filter(listing -> !listing.getOwner().getId().equals(viewer.getId()))
                .map(listing -> new ServiceOption(listing.getId(), listing.getName(),
                        listing.getOwner().getUsername(), listing.getAppointmentDurationMinutes()))
                .toList();
    }

    public List<ServiceOption> myServices() {
        User provider = user();
        return listings.findByOwnerId(provider.getId()).stream()
                .filter(listing -> listing.getType() == ListingType.SERVICE
                        && listing.getStatus() == ListingStatus.ACTIVE)
                .map(listing -> new ServiceOption(listing.getId(), listing.getName(),
                        provider.getUsername(), listing.getAppointmentDurationMinutes()))
                .toList();
    }

    public List<AvailabilitySummary> myAvailability() {
        User provider = user();
        return availability.findForProvider(provider.getId(), Instant.now()).stream()
                .filter(slot -> slot.getListing().getStatus() == ListingStatus.ACTIVE)
                .map(slot -> new AvailabilitySummary(slot.getId(), slot.getListing().getId(),
                        slot.getListing().getName(), slot.getStartAt(), slot.getEndAt(),
                        hasActiveBooking(slot.getListing().getId(), slot.getStartAt(), slot.getEndAt())))
                .toList();
    }

    @Transactional
    public Long addAvailability(Long listingId, LocalDate date, LocalTime time) {
        User provider = user();
        Listing listing = managedService(listingId, provider);
        if (date == null || time == null) {
            throw new IllegalArgumentException("Choose an available date and start time.");
        }
        LocalDateTime localStart = LocalDateTime.of(date, time);
        if (!localStart.isAfter(LocalDateTime.now(BOOKING_ZONE))) {
            throw new IllegalArgumentException("Availability must start in the future.");
        }
        LocalDateTime localEnd = localStart.plusMinutes(listing.getAppointmentDurationMinutes());
        if (!localEnd.toLocalDate().equals(date)) {
            throw new IllegalArgumentException("The appointment must end before midnight.");
        }
        Instant start = toInstant(localStart);
        Instant end = toInstant(localEnd);
        if (availability.findOverlapping(listingId, start, end).stream()
                .anyMatch(slot -> start.isBefore(slot.getEndAt()) && end.isAfter(slot.getStartAt()))) {
            throw new IllegalArgumentException("This time overlaps availability already on your calendar.");
        }
        return availability.saveAndFlush(new AvailabilitySlot(listing, start, end)).getId();
    }

    @Transactional
    public void removeAvailability(Long slotId) {
        User provider = user();
        if (slotId == null) throw new IllegalArgumentException("Select an availability slot.");
        AvailabilitySlot slot = availability.findForUpdate(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Availability not found."));
        if (!slot.getListing().getOwner().getId().equals(provider.getId())) {
            throw new AccessDeniedException("Only the service provider can edit availability.");
        }
        if (hasActiveBooking(slot.getListing().getId(), slot.getStartAt(), slot.getEndAt())) {
            throw new IllegalArgumentException("This time has a booking request or confirmation and cannot be removed.");
        }
        availability.delete(slot);
    }

    public List<AvailabilityOption> availableSlots(Long listingId, LocalDate date) {
        if (date == null || date.isBefore(LocalDate.now(BOOKING_ZONE))) {
            throw new IllegalArgumentException("Choose today or a future date.");
        }
        return availableSlots(listingId, date.atStartOfDay(BOOKING_ZONE).toInstant(),
                date.plusDays(1).atStartOfDay(BOOKING_ZONE).toInstant());
    }

    public List<AvailabilityOption> availableSlots(Long listingId, YearMonth month) {
        if (month == null) throw new IllegalArgumentException("Choose a calendar month.");
        return availableSlots(listingId, month.atDay(1).atStartOfDay(BOOKING_ZONE).toInstant(),
                month.plusMonths(1).atDay(1).atStartOfDay(BOOKING_ZONE).toInstant());
    }

    private List<AvailabilityOption> availableSlots(Long listingId, Instant from, Instant to) {
        user();
        activeService(listingId);
        return availability.findForListingAndRange(listingId, from, to).stream()
                .filter(slot -> slot.getStartAt().isAfter(Instant.now()))
                .filter(slot -> !hasActiveBooking(listingId, slot.getStartAt(), slot.getEndAt()))
                .map(slot -> new AvailabilityOption(slot.getId(), slot.getStartAt(), slot.getEndAt()))
                .toList();
    }

    public List<BookingSummary> getMyBookings() {
        User viewer = user();
        return bookings.findForParticipant(viewer.getId()).stream()
                .map(booking -> summary(booking, viewer.getId())).toList();
    }

    public List<BusySlot> getApprovedSlots(Long listingId, LocalDate date) {
        user();
        Listing listing = activeService(listingId);
        if (date == null || date.isBefore(LocalDate.now(BOOKING_ZONE))) {
            throw new IllegalArgumentException("Choose today or a future date.");
        }
        Instant start = date.atStartOfDay(BOOKING_ZONE).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(BOOKING_ZONE).toInstant();
        return bookings.findOverlapping(listing.getId(), BookingStatus.APPROVED, start, end).stream()
                .map(booking -> new BusySlot(booking.getStartAt(), booking.getEndAt())).toList();
    }

    @Transactional
    public Long requestBooking(Long listingId, LocalDate date, LocalTime time) {
        User buyer = user();
        Listing listing = activeService(listingId);
        if (listing.getOwner().getId().equals(buyer.getId())) {
            throw new IllegalArgumentException("You cannot book your own service.");
        }
        if (date == null || time == null) {
            throw new IllegalArgumentException("Choose a date and time for your booking.");
        }
        LocalDateTime localStart = LocalDateTime.of(date, time);
        if (!localStart.isAfter(LocalDateTime.now(BOOKING_ZONE))) {
            throw new IllegalArgumentException("Choose a future appointment time.");
        }
        Instant start = toInstant(localStart);
        AvailabilitySlot slot = availability.findForUpdateByListingAndStart(listing.getId(), start)
                .orElseThrow(() -> new IllegalArgumentException("That time is not on the provider's availability calendar."));
        if (hasActiveBooking(listing.getId(), slot.getStartAt(), slot.getEndAt())) {
            throw new IllegalArgumentException("That time already has a booking request. Choose another time.");
        }
        return bookings.saveAndFlush(new Booking(buyer, listing,
                slot.getStartAt(), slot.getEndAt())).getId();
    }

    @Transactional
    public void approve(Long bookingId) {
        User provider = user();
        Booking booking = bookingForUpdate(bookingId);
        Listing listing = listings.findForManagement(booking.getListing().getId())
                .orElseThrow(() -> new IllegalArgumentException("Service not found."));
        requireProvider(provider, listing);
        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new IllegalArgumentException("This service is no longer available for booking.");
        }
        if (!booking.getStartAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Past booking requests cannot be approved.");
        }
        if (bookings.findOverlapping(listing.getId(), BookingStatus.APPROVED,
                booking.getStartAt(), booking.getEndAt()).stream()
                .anyMatch(other -> !other.getId().equals(booking.getId()))) {
            throw new IllegalArgumentException("This time is already reserved by another approved booking.");
        }
        booking.approve();
    }

    @Transactional
    public void confirmByBuyer(Long bookingId) {
        User buyer = user();
        Booking booking = bookingForUpdate(bookingId);
        if (!booking.getStartAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Past booking appointments cannot be confirmed.");
        }
        booking.confirmByBuyer(buyer);
    }

    @Transactional
    public void decline(Long bookingId) {
        User provider = user();
        Booking booking = bookingForUpdate(bookingId);
        Listing listing = booking.getListing();
        requireProvider(provider, listing);
        booking.decline();
    }

    @Transactional
    public void cancel(Long bookingId) {
        User buyer = user();
        Booking booking = bookingForUpdate(bookingId);
        if (!booking.getBuyer().getId().equals(buyer.getId())) {
            throw new AccessDeniedException("Only the person who requested this booking can cancel it.");
        }
        if (!booking.getStartAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("A booking cannot be cancelled after its start time.");
        }
        booking.cancel();
    }

    private Listing activeService(Long listingId) {
        if (listingId == null) throw new IllegalArgumentException("Select a service.");
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Service not found."));
        if (listing.getType() != ListingType.SERVICE || listing.getStatus() != ListingStatus.ACTIVE) {
            throw new IllegalArgumentException("This service is not available for booking.");
        }
        return listing;
    }

    private Listing managedService(Long listingId, User provider) {
        if (listingId == null) throw new IllegalArgumentException("Select a service.");
        Listing listing = listings.findForManagement(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Service not found."));
        if (!listing.getOwner().getId().equals(provider.getId())) {
            throw new AccessDeniedException("Only the service provider can edit availability.");
        }
        if (listing.getType() != ListingType.SERVICE || listing.getStatus() != ListingStatus.ACTIVE) {
            throw new IllegalArgumentException("Availability can only be set for an active service.");
        }
        return listing;
    }

    private boolean hasActiveBooking(Long listingId, Instant start, Instant end) {
        return availability.countActiveBookingsOverlappingForSlot(listingId, start, end) > 0;
    }

    private Booking bookingForUpdate(Long bookingId) {
        if (bookingId == null) throw new IllegalArgumentException("Select a booking.");
        return bookings.findForUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found."));
    }

    private static void requireProvider(User provider, Listing listing) {
        if (!listing.getOwner().getId().equals(provider.getId())) {
            throw new AccessDeniedException("Only the service provider can respond to this request.");
        }
    }

    private static Instant toInstant(LocalDateTime localTime) {
        var offsets = BOOKING_ZONE.getRules().getValidOffsets(localTime);
        if (offsets.isEmpty()) throw new IllegalArgumentException("That local time does not exist due to a clock change.");
        if (offsets.size() > 1) throw new IllegalArgumentException("That local time occurs twice due to a clock change. Choose another time.");
        return localTime.atOffset(offsets.getFirst()).toInstant();
    }

    private static BookingSummary summary(Booking booking, Long viewerId) {
        return new BookingSummary(booking.getId(), booking.getListing().getId(),
                booking.getListing().getName(), booking.getBuyer().getUsername(),
                booking.getListing().getOwner().getUsername(),
                booking.getListing().getOwner().getId().equals(viewerId),
                booking.getStartAt(), booking.getEndAt(), booking.getStatus(), booking.isBuyerConfirmed());
    }
}

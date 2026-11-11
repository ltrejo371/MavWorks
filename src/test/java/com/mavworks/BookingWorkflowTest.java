package com.mavworks;

import com.mavworks.model.*;
import com.mavworks.repository.*;
import com.mavworks.service.BookingService;
import com.mavworks.service.ListingManagementService;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    @Autowired BookingService service;
    @Autowired ListingManagementService listingManagement;

    private User buyer;
    private User provider;
    private User stranger;
    private Listing listing;

    @BeforeEach
    void setUp() {
        bookings.deleteAll();
        listings.deleteAll();
        users.deleteAll();
        buyer = account("booking-buyer");
        provider = account("booking-provider");
        stranger = account("booking-stranger");
        listing = serviceListing("Tutoring", 30);
        login(buyer);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestUsesProviderConfiguredDurationAndProviderCanApprove() {
        LocalDate date = LocalDate.now(BookingService.BOOKING_ZONE).plusDays(2);
        Long id = service.requestBooking(listing.getId(), date, LocalTime.NOON);

        var saved = bookings.findById(id).orElseThrow();
        assertEquals(BookingStatus.PENDING, saved.getStatus());
        assertEquals(Duration.ofMinutes(30), Duration.between(saved.getStartAt(), saved.getEndAt()));

        login(provider);
        service.approve(id);
        assertEquals(BookingStatus.APPROVED, bookings.findById(id).orElseThrow().getStatus());

        login(buyer);
        assertEquals(1, service.getApprovedSlots(listing.getId(), date).size());
        assertThrows(IllegalArgumentException.class,
                () -> service.requestBooking(listing.getId(), date, LocalTime.of(12, 15)));
        assertDoesNotThrow(() -> service.requestBooking(listing.getId(), date, LocalTime.of(12, 30)));
    }

    @Test
    void concurrentRequestsCanBePendingButOnlyOneOverlappingRequestCanBeApproved() {
        LocalDate date = LocalDate.now(BookingService.BOOKING_ZONE).plusDays(3);
        Long first = service.requestBooking(listing.getId(), date, LocalTime.of(10, 0));
        Long second = service.requestBooking(listing.getId(), date, LocalTime.of(10, 15));

        login(provider);
        service.approve(first);
        assertThrows(IllegalArgumentException.class, () -> service.approve(second));
        assertEquals(BookingStatus.PENDING, bookings.findById(second).orElseThrow().getStatus());
        service.decline(second);
        assertEquals(BookingStatus.DECLINED, bookings.findById(second).orElseThrow().getStatus());
    }

    @Test
    void onlyProviderCanApproveAndOnlyBuyerCanCancel() {
        LocalDate date = LocalDate.now(BookingService.BOOKING_ZONE).plusDays(2);
        Long id = service.requestBooking(listing.getId(), date, LocalTime.of(14, 0));
        login(stranger);
        assertThrows(AccessDeniedException.class, () -> service.approve(id));
        assertThrows(AccessDeniedException.class, () -> service.cancel(id));

        login(provider);
        assertThrows(AccessDeniedException.class, () -> service.cancel(id));
        service.approve(id);
        login(buyer);
        service.cancel(id);
        assertEquals(BookingStatus.CANCELLED, bookings.findById(id).orElseThrow().getStatus());
        assertDoesNotThrow(() -> service.requestBooking(listing.getId(), date, LocalTime.of(14, 0)));
    }

    @Test
    void providerCanChangeServiceDurationAndCannotBookTheirOwnService() {
        login(provider);
        listingManagement.updateDetails(listing.getId(), listing.getName(), listing.getDescription(),
                listing.getPrice(), listing.getCategory(), 75);
        assertEquals(75, listings.findById(listing.getId()).orElseThrow().getAppointmentDurationMinutes());
        assertTrue(service.availableServices().isEmpty());

        login(buyer);
        LocalDate date = LocalDate.now(BookingService.BOOKING_ZONE).plusDays(2);
        Long id = service.requestBooking(listing.getId(), date, LocalTime.of(11, 0));
        var saved = bookings.findById(id).orElseThrow();
        assertEquals(Duration.ofMinutes(75), Duration.between(saved.getStartAt(), saved.getEndAt()));
    }

    @Test
    void rejectsPastTimesSelfBookingAndProductListings() {
        assertThrows(IllegalArgumentException.class,
                () -> service.requestBooking(listing.getId(), LocalDate.now(BookingService.BOOKING_ZONE).minusDays(1), LocalTime.NOON));
        assertThrows(IllegalArgumentException.class,
                () -> service.requestBooking(listing.getId(), LocalDate.now(BookingService.BOOKING_ZONE), LocalTime.MIDNIGHT));
        login(provider);
        assertThrows(IllegalArgumentException.class,
                () -> service.requestBooking(listing.getId(),
                        LocalDate.now(BookingService.BOOKING_ZONE).plusDays(1), LocalTime.NOON));

        var product = new Listing();
        product.setOwner(provider);
        product.setName("Textbook");
        product.setDescription("A book");
        product.setPrice(BigDecimal.TEN);
        product.setType(ListingType.PRODUCT);
        product = listings.saveAndFlush(product);
        Long productId = product.getId();
        login(buyer);
        assertThrows(IllegalArgumentException.class,
                () -> service.requestBooking(productId,
                        LocalDate.now(BookingService.BOOKING_ZONE).plusDays(1), LocalTime.NOON));
    }

    @Test
    void rejectsAppointmentsThatWouldCrossMidnight() {
        listing.setAppointmentDurationMinutes(90);
        listings.saveAndFlush(listing);
        LocalDate date = LocalDate.now(BookingService.BOOKING_ZONE).plusDays(2);
        assertThrows(IllegalArgumentException.class,
                () -> service.requestBooking(listing.getId(), date, LocalTime.of(23, 0)));
    }

    private User account(String username) {
        var user = new User();
        user.setUsername(username);
        user.setEmail(username + "@mavs.uta.edu");
        user.setHashedPassword("test-only");
        return users.saveAndFlush(user);
    }

    private Listing serviceListing(String name, int minutes) {
        var serviceListing = new Listing();
        serviceListing.setOwner(provider);
        serviceListing.setName(name);
        serviceListing.setDescription("A service");
        serviceListing.setPrice(BigDecimal.TEN);
        serviceListing.setType(ListingType.SERVICE);
        serviceListing.setAppointmentDurationMinutes(minutes);
        return listings.saveAndFlush(serviceListing);
    }

    private void login(User user) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user.getUsername(), null, List.of()));
        SecurityContextHolder.setContext(context);
    }
}

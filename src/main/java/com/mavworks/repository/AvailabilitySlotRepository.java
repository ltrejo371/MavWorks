package com.mavworks.repository;

import com.mavworks.model.AvailabilitySlot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Long> {
    @Query("""
            select s from AvailabilitySlot s join fetch s.listing l
            where l.id = :listingId and s.startAt >= :from and s.startAt < :to
            order by s.startAt
            """)
    List<AvailabilitySlot> findForListingAndRange(@Param("listingId") Long listingId,
            @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select s from AvailabilitySlot s join fetch s.listing l
            where l.id = :listingId and s.startAt < :to and s.endAt > :from
            order by s.startAt
            """)
    List<AvailabilitySlot> findOverlapping(@Param("listingId") Long listingId,
            @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select s from AvailabilitySlot s join fetch s.listing l
            where l.owner.id = :ownerId and s.startAt >= :from
            order by l.name, s.startAt
            """)
    List<AvailabilitySlot> findForProvider(@Param("ownerId") Long ownerId, @Param("from") Instant from);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AvailabilitySlot s join fetch s.listing where s.id = :id")
    Optional<AvailabilitySlot> findForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AvailabilitySlot s join fetch s.listing where s.listing.id = :listingId and s.startAt = :startAt")
    Optional<AvailabilitySlot> findForUpdateByListingAndStart(@Param("listingId") Long listingId,
            @Param("startAt") Instant startAt);

    @Query("""
            select count(b) from Booking b where b.listing.id = :listingId
              and b.status in (com.mavworks.model.BookingStatus.PENDING,
                  com.mavworks.model.BookingStatus.APPROVED)
              and b.startAt < :endAt and b.endAt > :startAt
            """)
    long countActiveBookingsOverlappingForSlot(@Param("listingId") Long listingId,
            @Param("startAt") Instant startAt, @Param("endAt") Instant endAt);
}

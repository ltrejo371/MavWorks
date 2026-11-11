package com.mavworks.repository;

import com.mavworks.model.Booking;
import com.mavworks.model.BookingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    @Query("""
            select b from Booking b
            join fetch b.listing l join fetch l.owner join fetch b.buyer
            where b.buyer.id = :userId or l.owner.id = :userId
            order by b.startAt desc, b.id desc
            """)
    List<Booking> findForParticipant(@Param("userId") Long userId);

    @Query("""
            select b from Booking b
            where b.listing.id = :listingId and b.status = :status
              and b.startAt < :endAt and b.endAt > :startAt
            order by b.startAt
            """)
    List<Booking> findOverlapping(@Param("listingId") Long listingId,
            @Param("status") BookingStatus status, @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findForUpdate(@Param("id") Long id);
}

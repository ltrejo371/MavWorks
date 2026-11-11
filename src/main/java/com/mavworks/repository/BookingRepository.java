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
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"buyer", "listing", "listing.owner", "requester", "conversation"})
    List<Booking> findByConversationIdOrderByStartAtDesc(Long conversationId);

    @Query("select b.id from Booking b where (b.status = :requested and (b.startAt <= :now or b.createdAt <= :cutoff)) or (b.status = :confirmed and b.endAt <= :now)")
    List<Long> findDue(@Param("requested") BookingStatus requested, @Param("confirmed") BookingStatus confirmed,
            @Param("now") Instant now, @Param("cutoff") Instant cutoff);

    @Query("""
            select b from Booking b where b.status = :status
            and (b.buyer.id in (:first, :second) or b.listing.owner.id in (:first, :second))
            and b.startAt < :end and b.endAt > :start
            """)
    List<Booking> findParticipantConflicts(@Param("first") Long first, @Param("second") Long second,
            @Param("status") BookingStatus status, @Param("start") Instant start, @Param("end") Instant end);
    @Query("""
            select b from Booking b
            join fetch b.listing l join fetch l.owner join fetch b.buyer join fetch b.requester join fetch b.conversation
            where b.buyer.id = :userId or l.owner.id = :userId
            order by b.startAt desc, b.id desc
            """)
    List<Booking> findForParticipant(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findForUpdate(@Param("id") Long id);
}

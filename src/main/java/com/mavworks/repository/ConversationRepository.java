package com.mavworks.repository;

import com.mavworks.model.Conversation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findFirstByBuyerIdAndListingIdOrderByIdDesc(Long buyerId, Long listingId);

    @Query("""
            select c from Conversation c join fetch c.listing l join fetch l.owner join fetch c.buyer
            where c.buyer.id = :userId or l.owner.id = :userId order by c.id desc
            """)
    List<Conversation> findForParticipant(@Param("userId") Long userId);
    @Query("""
            select c from Conversation c join fetch c.listing l
            where c.buyer.id = :buyerId and l.owner.id = :providerId
              and c.saleConfirmedAt is not null and c.closedAt >= c.saleConfirmedAt
            order by c.id
            """)
    List<Conversation> findCompletedSales(@Param("buyerId") Long buyerId, @Param("providerId") Long providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findForUpdate(@Param("id") Long id);
}

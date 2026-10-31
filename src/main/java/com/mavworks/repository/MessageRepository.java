package com.mavworks.repository;

import com.mavworks.model.Message;
import com.mavworks.dto.MessageSummary;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {
    interface LastActivity {
        Long getConversationId();
        java.time.Instant getSentAt();
    }

    @Query("""
            select m.conversation.id as conversationId, max(m.sentAt) as sentAt
            from Message m where m.conversation.buyer.id = :userId
                or m.conversation.listing.owner.id = :userId
            group by m.conversation.id
            """)
    List<LastActivity> findLastActivityForParticipant(@Param("userId") Long userId);

    boolean existsByConversationId(Long conversationId);

    @Query("""
            select new com.mavworks.dto.MessageSummary(m.id, m.sender.id, m.sender.username, m.body, m.sentAt)
            from Message m where m.conversation.id = :conversationId and (:beforeId is null or m.id < :beforeId)
            order by m.id desc
            """)
    List<MessageSummary> before(@Param("conversationId") Long conversationId, @Param("beforeId") Long beforeId, Pageable page);

    @Query("""
            select new com.mavworks.dto.MessageSummary(m.id, m.sender.id, m.sender.username, m.body, m.sentAt)
            from Message m where m.conversation.id = :conversationId and m.id > :afterId order by m.id
            """)
    List<MessageSummary> after(@Param("conversationId") Long conversationId, @Param("afterId") Long afterId, Pageable page);
}

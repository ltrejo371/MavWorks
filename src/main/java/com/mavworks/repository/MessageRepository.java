package com.mavworks.repository;

import com.mavworks.model.Message;
import com.mavworks.dto.MessageSummary;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {
    interface UnreadConversation {
        Long getConversationId();
        String getListingName();
        String getSenderName();
        Long getUnreadCount();
        Long getLatestMessageId();
    }

    @Query("""
            select m.conversation.id as conversationId, m.conversation.listing.name as listingName,
                m.sender.username as senderName, count(m) as unreadCount, max(m.id) as latestMessageId
            from Message m
            where m.readAt is null and m.sender.id <> :userId
              and (m.conversation.buyer.id = :userId or m.conversation.listing.owner.id = :userId)
              and not exists (select b.id from UserBlock b
                where (b.blocker.id = :userId and b.blocked.id = m.sender.id)
                   or (b.blocked.id = :userId and b.blocker.id = m.sender.id))
            group by m.conversation.id, m.conversation.listing.name, m.sender.username
            order by max(m.id) desc
            """)
    List<UnreadConversation> findUnreadForParticipant(@Param("userId") Long userId);

    @Modifying
    @Query("""
            update Message m set m.readAt = :now
            where m.conversation.id = :conversationId and m.sender.id <> :userId
              and m.id <= :throughId and m.readAt is null
            """)
    int markRead(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
            @Param("throughId") Long throughId, @Param("now") java.time.Instant now);

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

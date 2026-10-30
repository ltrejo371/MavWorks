package com.mavworks.service;

import com.mavworks.dto.MessageSummary;
import com.mavworks.model.*;
import com.mavworks.repository.*;
import com.mavworks.security.CurrentUser;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MessagingService {
    public record ConversationSummary(Long id, Long listingId, String listingName, String otherUser, boolean closed) {}
    public record Chat(Long id, Long listingId, String listingName, Long providerId, String otherUser,
            boolean provider, boolean closed, List<MessageSummary> messages) {}
    private final CurrentUser current;
    private final UserRepository users;
    private final ListingRepository listings;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final UserBlockRepository blocks;
    private static final PageRequest PAGE = PageRequest.of(0, 100);

    public MessagingService(CurrentUser current, UserRepository users, ListingRepository listings,
            ConversationRepository conversations, MessageRepository messages, UserBlockRepository blocks) {
        this.current = current;
        this.users = users;
        this.listings = listings;
        this.conversations = conversations;
        this.messages = messages;
        this.blocks = blocks;
    }

    private User user(boolean lock) {
        String username = current.username().orElseThrow(() -> new AccessDeniedException("Sign in to message a provider."));
        return (lock ? users.findForMessaging(username) : users.findByUsername(username))
                .orElseThrow(() -> new AccessDeniedException("Sign in to message a provider."));
    }

    private void checkBlocks(Long buyerId, Listing listing) {
        Long providerId = listing.getOwner().getId();
        if (blocks.existsByBlockerIdAndBlockedId(buyerId, providerId)
                || blocks.existsByBlockerIdAndBlockedId(providerId, buyerId)) {
            throw new MessagingBlockedException(listing.getId());
        }
    }

    private Conversation accessible(Long id, User user, boolean lock) {
        if (id == null) throw new IllegalArgumentException("Select a conversation.");
        var conversation = (lock ? conversations.findForUpdate(id) : conversations.findById(id))
                .orElseThrow(() -> new AccessDeniedException("Conversation not available."));
        if (!conversation.hasParticipant(user)) throw new AccessDeniedException("Conversation not available.");
        checkBlocks(conversation.getBuyer().getId(), conversation.getListing());
        return conversation;
    }

    @Transactional
    public Long startConversation(Long listingId) {
        // Serialize starts by buyer so concurrent clicks reuse the same conversation.
        User buyer = user(true);
        if (listingId == null) throw new IllegalArgumentException("Select a listing.");
        var listing = listings.findById(listingId).orElseThrow(() -> new IllegalArgumentException("Listing not found."));
        if (listing.getOwner().getId().equals(buyer.getId())) throw new IllegalArgumentException("You cannot message yourself about your own listing.");
        checkBlocks(buyer.getId(), listing);
        var existing = conversations.findFirstByBuyerIdAndListingIdOrderByIdDesc(buyer.getId(), listingId);
        if (existing.isPresent()) return existing.get().getId();
        if (listing.getStatus() != ListingStatus.ACTIVE) throw new IllegalArgumentException("This listing is no longer available for new conversations.");
        return conversations.saveAndFlush(new Conversation(buyer, listing)).getId();
    }

    @Transactional
    public Long sendMessage(Long conversationId, String text) {
        var sender = user(false);
        var conversation = accessible(conversationId, sender, true);
        return messages.saveAndFlush(sender.sendMessage(conversation, text)).getId();
    }

    public List<ConversationSummary> getMyConversations() {
        var user = user(false);
        return conversations.findForParticipant(user.getId()).stream().map(c -> new ConversationSummary(
                c.getId(), c.getListing().getId(), c.getListing().getName(),
                c.getBuyer().getId().equals(user.getId()) ? c.getListing().getOwner().getUsername() : c.getBuyer().getUsername(),
                c.getClosedAt() != null)).toList();
    }

    public Chat getChat(Long id, Long afterId) {
        var user = user(false);
        var c = accessible(id, user, false);
        var items = afterId == null ? chronological(messages.before(id, null, PAGE)) : messages.after(id, afterId, PAGE);
        boolean provider = c.getListing().getOwner().getId().equals(user.getId());
        return new Chat(c.getId(), c.getListing().getId(), c.getListing().getName(), c.getListing().getOwner().getId(),
                provider ? c.getBuyer().getUsername() : c.getListing().getOwner().getUsername(), provider,
                c.getClosedAt() != null, items);
    }

    public List<MessageSummary> getOlderMessages(Long id, Long beforeId) {
        accessible(id, user(false), false);
        return chronological(messages.before(id, beforeId, PAGE));
    }

    private List<MessageSummary> chronological(List<MessageSummary> items) {
        var ordered = new ArrayList<>(items);
        Collections.reverse(ordered);
        return ordered;
    }
}

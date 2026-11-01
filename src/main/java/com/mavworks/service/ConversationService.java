package com.mavworks.service;

import com.mavworks.repository.*;
import com.mavworks.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mavworks.model.ListingStatus;
import com.mavworks.model.ListingType;

/** Sale-completion hook for the messaging workflow; no client may supply the confirming identity. */
@Service
public class ConversationService {
    private final CurrentUser currentUser;
    private final UserRepository users;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final UserBlockRepository blocks;
    private final ListingRepository listings;

    public ConversationService(
            CurrentUser currentUser,
            UserRepository users,
            ConversationRepository conversations,
            MessageRepository messages,
            UserBlockRepository blocks,
            ListingRepository listings) {
        this.currentUser = currentUser;
        this.users = users;
        this.conversations = conversations;
        this.messages = messages;
        this.blocks = blocks;
        this.listings = listings;
    }

    @Transactional
    public void confirmSaleAndClose(Long conversationId) {
        var provider = currentUser.username().flatMap(users::findByUsername)
                .orElseThrow(() -> new AccessDeniedException("Sign in to confirm a sale."));
        if (conversationId == null) throw new IllegalArgumentException("Select a conversation.");
        var conversation = conversations.findForUpdate(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found."));
        var listing = listings.findForManagement(
                        conversation.getListing().getId())
                .orElseThrow(() -> new IllegalArgumentException("Listing not found."));
        if (!listing.getOwner().getId().equals(provider.getId())) {
            throw new AccessDeniedException("Only the provider can confirm this sale.");
        }
        Long buyerId = conversation.getBuyer().getId();
        if (blocks.existsByBlockerIdAndBlockedId(buyerId, provider.getId())
                || blocks.existsByBlockerIdAndBlockedId(provider.getId(), buyerId)) {
            throw new MessagingBlockedException(conversation.getListing().getId());
        }
        if (!messages.existsByConversationId(conversationId)) {
            throw new IllegalArgumentException("Exchange a message before confirming the sale.");
        }
        if (listing.getStatus() == ListingStatus.SOLD) {
            throw new IllegalArgumentException("This listing has already been sold.");
        }

        if (listing.getStatus() == ListingStatus.REMOVED) {
            throw new IllegalArgumentException(
                    "A removed listing cannot have a sale confirmed.");
        }
        conversation.confirmSale(provider);
        if (listing.getType() == ListingType.PRODUCT) {
            listing.markSold();
        }
        conversation.closeConversation();
    }
}

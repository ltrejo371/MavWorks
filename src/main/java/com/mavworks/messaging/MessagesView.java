package com.mavworks.messaging;

import com.mavworks.base.ui.ViewTitle;
import com.mavworks.service.MessagingService;
import com.mavworks.service.ImageStorageService;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import org.springframework.dao.DataAccessException;

@Route("messages")
@PageTitle("Messages")
@Menu(order = 2, icon = "vaadin:comments", title = "Messages")
@PermitAll
@CssImport("./styles/messages.css")
public class MessagesView extends VerticalLayout implements BeforeEnterObserver {
    private final MessagingService service;
    private final ImageStorageService images;
    private final Div conversations = new Div();
    private com.vaadin.flow.shared.Registration poll;
    private int previousPollInterval;
    private java.util.List<MessagingService.ConversationSummary> displayed;
    private final Paragraph feedback = new Paragraph();
    private java.util.Map<Long, Long> unreadCounts = java.util.Map.of();

    public MessagesView(MessagingService service, ImageStorageService images) {
        this.service = service;
        this.images = images;
        addClassName("messages-inbox");
        conversations.addClassName("messages-list");
        Div heading = new Div(new ViewTitle("Messages"), new Paragraph("Your conversations about marketplace listings."));
        Div header = new Div(heading);
        header.addClassName("messages-header");
        feedback.getElement().setAttribute("role", "status");
        feedback.setVisible(false);
        add(header, feedback, conversations);
    }

    @Override public void beforeEnter(BeforeEnterEvent event) { refresh(); }

    @Override protected void onAttach(com.vaadin.flow.component.AttachEvent event) {
        super.onAttach(event);
        previousPollInterval = event.getUI().getPollInterval();
        event.getUI().setPollInterval(previousPollInterval > 0 ? Math.min(previousPollInterval, 5000) : 5000);
        poll = event.getUI().addPollListener(ignored -> refresh());
    }

    @Override protected void onDetach(com.vaadin.flow.component.DetachEvent event) {
        if (poll != null) { poll.remove(); poll = null; }
        event.getUI().setPollInterval(previousPollInterval);
        super.onDetach(event);
    }

    private void refresh() {
        try {
            var items = service.getMyConversations();
            var unread = service.getUnreadNotifications().stream().collect(java.util.stream.Collectors.toMap(
                    MessagingService.UnreadNotification::conversationId, MessagingService.UnreadNotification::count));
            feedback.setVisible(false);
            if (items.equals(displayed) && unread.equals(unreadCounts)) return;
            displayed = java.util.List.copyOf(items);
            unreadCounts = unread;
            conversations.removeAll();
            if (items.isEmpty()) conversations.add(new Paragraph("No conversations yet. Open a listing and select Message."));
            for (var c : items) conversations.add(card(c));
        } catch (DataAccessException ex) {
            feedback.setText("Messages could not be updated. Retrying automatically.");
            feedback.setVisible(true);
        }
    }
    private Anchor card(MessagingService.ConversationSummary conversation) {
        Anchor card = new Anchor("messages/" + conversation.id(), "");
        card.addClassName("message-card");
        card.getElement().setAttribute("aria-label", "Conversation with " + conversation.otherUser() + " about " + conversation.listingName());
        Div preview = new Div(new Span("No image"));
        preview.addClassName("message-preview");
        String reference = conversation.imageReference();
        if (reference != null && !reference.isBlank()) {
            Image image = new Image(DownloadHandler.fromInputStream(event -> {
                try {
                    return new DownloadResponse(images.open(reference), reference,
                            reference.endsWith("png") ? "image/png" : "image/jpeg", -1);
                } catch (java.io.IOException | IllegalArgumentException | org.springframework.security.access.AccessDeniedException ex) {
                    return DownloadResponse.error(404);
                }
            }), conversation.listingName());
            image.getElement().setAttribute("loading", "lazy");
            image.getElement().setAttribute("decoding", "async");
            image.getElement().addEventListener("error", event -> image.setVisible(false));
            preview.add(image);
        }
        Div details = new Div(new H2(conversation.listingName()), new Paragraph("With " + conversation.otherUser()));
        details.addClassName("message-card-details");
        Span timestamp = new Span("No messages yet");
        timestamp.addClassName("message-last-sent");
        if (conversation.lastMessageAt() != null) {
            String instant = conversation.lastMessageAt().toString();
            timestamp.setText("Last message · " + java.time.format.DateTimeFormatter.ofPattern("MMM d, uuuu HH:mm 'UTC'")
                    .withZone(java.time.ZoneOffset.UTC).format(conversation.lastMessageAt()));
            timestamp.getElement().setAttribute("data-sent-at", instant);
            timestamp.addAttachListener(event -> timestamp.getElement().executeJs(
                    "const date = new Date($0); this.textContent = 'Last message · ' + date.toLocaleString(undefined, "
                    + "{month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit'}); this.title = date.toLocaleString();", instant));
        }
        Div tags = new Div(timestamp); tags.addClassName("message-card-tags");
        long unread = unreadCounts.getOrDefault(conversation.id(), 0L);
        if (unread > 0) {
            Span badge = new Span(unread + " unread");
            badge.getElement().getThemeList().add("badge primary");
            tags.add(badge);
        }
        if (conversation.closed()) {
            Span closed = new Span("Closed"); closed.addClassName("message-closed"); tags.add(closed);
        }
        details.add(tags); card.add(preview, details);
        return card;
    }
}

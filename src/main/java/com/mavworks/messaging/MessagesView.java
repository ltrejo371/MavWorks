package com.mavworks.messaging;

import com.mavworks.base.ui.ViewTitle;
import com.mavworks.service.MessagingService;
import com.mavworks.service.LocalImageStorageService;
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
@Menu(order = 2, title = "Messages")
@PermitAll
@CssImport("./styles/messages.css")
public class MessagesView extends VerticalLayout implements BeforeEnterObserver {
    private final MessagingService service;
    private final LocalImageStorageService images;
    private final Div conversations = new Div();

    public MessagesView(MessagingService service, LocalImageStorageService images) {
        this.service = service;
        this.images = images;
        addClassName("messages-inbox");
        conversations.addClassName("messages-list");
        Div heading = new Div(new ViewTitle("Messages"), new Paragraph("Your conversations about marketplace listings."));
        Div header = new Div(heading, new Button("Refresh", event -> refresh()));
        header.addClassName("messages-header");
        add(header, conversations);
    }

    @Override public void beforeEnter(BeforeEnterEvent event) { refresh(); }

    private void refresh() {
        conversations.removeAll();
        try {
            var items = service.getMyConversations();
            if (items.isEmpty()) conversations.add(new Paragraph("No conversations yet. Open a listing and select Message."));
            for (var c : items) {
                conversations.add(card(c));
            }
        } catch (DataAccessException ex) {
            conversations.add(new Paragraph("Messages could not be loaded. Select Refresh to try again."));
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
        if (conversation.closed()) {
            Span closed = new Span("Closed"); closed.addClassName("message-closed"); tags.add(closed);
        }
        details.add(tags); card.add(preview, details);
        return card;
    }
}

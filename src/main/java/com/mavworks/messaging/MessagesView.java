package com.mavworks.messaging;

import com.mavworks.base.ui.ViewTitle;
import com.mavworks.service.MessagingService;
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
public class MessagesView extends VerticalLayout implements BeforeEnterObserver {
    private final MessagingService service;
    private final VerticalLayout conversations = new VerticalLayout();

    public MessagesView(MessagingService service) {
        this.service = service;
        conversations.setPadding(false);
        add(new ViewTitle("Messages"), new Button("Refresh", event -> refresh()), conversations);
    }

    @Override public void beforeEnter(BeforeEnterEvent event) { refresh(); }

    private void refresh() {
        conversations.removeAll();
        try {
            var items = service.getMyConversations();
            if (items.isEmpty()) conversations.add(new Paragraph("No conversations yet. Open a listing and select Message."));
            for (var c : items) {
                conversations.add(new Anchor("messages/" + c.id(), c.listingName() + " · " + c.otherUser()
                        + (c.closed() ? " · Closed" : "")));
            }
        } catch (DataAccessException ex) {
            conversations.add(new Paragraph("Messages could not be loaded. Select Refresh to try again."));
        }
    }
}

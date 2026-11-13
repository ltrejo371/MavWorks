package com.mavworks.messaging;

import com.mavworks.service.MessagingService;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.shared.Registration;
import java.util.List;

/** In-app unread notifications, backed by persisted message read status. */
public class MessageNotifications extends Div {
    private final MessagingService service;
    private final Button button = new Button("0");
    private final Div items = new Div();
    private final Dialog dialog = new Dialog();
    private Registration poll;
    private int previousPollInterval;
    private boolean initialized;
    private long latestSeen;

    public MessageNotifications(MessagingService service) {
        this.service = service;
        addClassName("message-notifications");
        button.setTooltipText("Message notifications");
        button.setIcon(com.vaadin.flow.component.icon.VaadinIcon.BELL.create());
        button.getElement().setAttribute("aria-live", "polite");
        button.addClickListener(e -> { refresh(); dialog.open(); });
        dialog.setHeaderTitle("Message notifications");
        dialog.setWidth("28rem");
        dialog.add(items, new Anchor("messages", "All messages"));
        dialog.getFooter().add(new Button("Close", e -> dialog.close()));
        add(button, dialog);
    }

    @Override protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        previousPollInterval = event.getUI().getPollInterval();
        event.getUI().setPollInterval(previousPollInterval > 0 ? Math.min(previousPollInterval, 5000) : 5000);
        poll = event.getUI().addPollListener(e -> refresh());
        refresh();
    }

    @Override protected void onDetach(DetachEvent event) {
        if (poll != null) { poll.remove(); poll = null; }
        event.getUI().setPollInterval(previousPollInterval);
        dialog.close();
        super.onDetach(event);
    }

    public void refresh() {
        try {
            List<MessagingService.UnreadNotification> unread = service.getUnreadNotifications();
            long count = unread.stream().mapToLong(MessagingService.UnreadNotification::count).sum();
            long latest = unread.stream().mapToLong(MessagingService.UnreadNotification::latestMessageId).max().orElse(0);
            button.setText(count > 99 ? "99+" : Long.toString(count));
            button.setAriaLabel(count + " unread messages. Open notifications");
            items.removeAll();
            if (unread.isEmpty()) items.add(new Paragraph("You're all caught up."));
            for (var n : unread) {
                Anchor link = new Anchor("messages/" + n.conversationId(), n.senderName() + " · " + n.listingName()
                        + " · " + n.count() + " unread");
                link.getStyle().set("display", "block").set("padding", "0.75rem 0");
                link.getElement().addEventListener("click", e -> dialog.close());
                items.add(link);
            }
            if (initialized && latest > latestSeen) {
                String path = getUI().map(ui -> ui.getInternals().getActiveViewLocation().getPath()).orElse("");
                boolean elsewhere = unread.stream().anyMatch(n -> n.latestMessageId() > latestSeen
                        && !path.equals("messages/" + n.conversationId()));
                if (elsewhere) Notification.show("You have new messages. Open message notifications to read them.",
                        5000, Notification.Position.TOP_END);
            }
            latestSeen = Math.max(latestSeen, latest);
            initialized = true;
        } catch (org.springframework.dao.DataAccessException | org.springframework.security.access.AccessDeniedException ex) {
            button.setText("!");
            button.setAriaLabel("Message notifications unavailable");
            items.removeAll();
            items.add(new Paragraph("Notifications could not be loaded. Reopen this panel to retry."));
        }
    }
}

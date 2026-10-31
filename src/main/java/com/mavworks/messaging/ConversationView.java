package com.mavworks.messaging;

import com.mavworks.dto.MessageSummary;
import com.mavworks.service.*;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.*;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.security.PermitAll;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.time.Duration;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;

@Route("messages/:conversationId")
@PageTitle("Conversation")
@PermitAll
@CssImport("./styles/conversation.css")
public class ConversationView extends VerticalLayout implements BeforeEnterObserver {
    private final MessagingService service;
    private final ConversationService sales;
    private final BlockService blocks;
    private final ReportService reports;
    private Long conversationId;
    private Long firstId;
    private Long lastId;
    private Long providerId;
    private boolean viewerIsProvider;
    private record MessageTime(java.time.Instant sentAt, Span timestamp) {}
    private final NavigableMap<Long, MessageTime> messageTimes = new TreeMap<>();
    private final VerticalLayout history = new VerticalLayout();
    private final TextArea draft = new TextArea();
    private final Paragraph feedback = new Paragraph();
    private final Paragraph state = new Paragraph();
    private final Button send = new Button("Send");
    private final Button confirm = new Button("Confirm sale and close");
    private final Button older = new Button("Load older messages");
    private Registration poll;
    private int previousPollInterval;

    public ConversationView(MessagingService service, ConversationService sales, BlockService blocks, ReportService reports) {
        this.service = service;
        this.sales = sales;
        this.blocks = blocks;
        this.reports = reports;
        addClassName("mav-chat");
        setPadding(false);
        setSpacing(false);
        history.setPadding(false);
        history.addClassName("mav-chat-history");
        history.getElement().setAttribute("aria-live", "polite");
        history.getElement().setAttribute("role", "log");
        history.getElement().setAttribute("aria-label", "Conversation messages");
        feedback.addClassName("mav-chat-feedback");
        state.addClassName("mav-chat-state");
        feedback.getElement().setAttribute("role", "status");
        draft.setMaxLength(4000);
        draft.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.EAGER);
        draft.setWidthFull();
        draft.setPlaceholder("Message");
        draft.setAriaLabel("Message");
        draft.addClassName("mav-chat-input");
        draft.setMaxHeight("9rem");
        draft.getElement().addEventListener("keydown", event -> {
            if (draft.isEnabled() && send.isEnabled() && conversationId != null) {
                // Carry the current input with Enter, even before the field's change event fires.
                draft.setValue(event.getEventData().path("event.target.value").asString(""));
                send();
            }
        }).addEventData("event.target.value").setFilter(
                "event.key === 'Enter' && !event.shiftKey && !event.ctrlKey && !event.altKey "
                + "&& !event.metaKey && !event.isComposing && (event.preventDefault() || true) && !event.repeat");
        send.setText("");
        var sendArrow = com.vaadin.flow.component.icon.VaadinIcon.ARROW_UP.create();
        sendArrow.setSize("20px");
        send.setIcon(sendArrow);
        send.getElement().setAttribute("aria-label", "Send");
        send.addClassName("mav-chat-send");
        older.addClassName("mav-chat-older");
        send.setDisableOnClick(true);
        send.addClickListener(event -> send());
        confirm.setDisableOnClick(true);
        confirm.addClickListener(event -> confirmSale());
        older.addClickListener(event -> loadOlder());
    }

    @Override public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        history.removeAll();
        messageTimes.clear();
        draft.clear();
        firstId = null;
        lastId = null;
        feedback.setText("");
        try {
            conversationId = Long.valueOf(event.getRouteParameters().get("conversationId").orElse(""));
            var chat = service.getChat(conversationId, null);
            providerId = chat.providerId();
            viewerIsProvider = chat.provider();
            Span avatar = new Span(chat.otherUser().substring(0, 1).toUpperCase(java.util.Locale.ROOT));
            avatar.addClassName("mav-chat-avatar");
            var person = new com.mavworks.base.ui.UserOptions(chat.otherUserId(), chat.otherUser(), blocks, reports, () -> {
                denied();
                getUI().ifPresent(ui -> ui.navigate("messages"));
            });
            person.addClassName("mav-chat-person");
            Span listing = new Span(chat.listingName());
            listing.addClassName("mav-chat-listing");
            Div identity = new Div(avatar, person, listing);
            identity.addClassName("mav-chat-identity");
            Anchor back = new Anchor("listings/" + chat.listingId(), "‹ Listing");
            back.addClassName("mav-chat-back");
            Button refresh = new Button("Refresh", click -> refresh());
            refresh.addClassName("mav-chat-refresh");
            Div header = new Div(back, identity, refresh);
            header.addClassName("mav-chat-header");
            Div composer = new Div(draft, send);
            composer.addClassName("mav-chat-composer");
            Div actions = new Div(new Anchor("profiles/" + chat.providerId(), "Provider profile and reviews"), confirm);
            actions.addClassName("mav-chat-actions");
            add(header, state, older, history, feedback, composer, actions);
            append(chat.messages());
            older.setVisible(chat.messages().size() == 100);
            updateState(chat);
        } catch (MessagingBlockedException ex) {
            Notification.show(ex.getMessage(), 7000, Notification.Position.MIDDLE);
            event.forwardTo("listings/" + ex.getListingId());
        } catch (IllegalArgumentException | AccessDeniedException ex) {
            conversationId = null;
            add(new Paragraph("Conversation not available."), new Anchor("messages", "Back to messages"));
        } catch (DataAccessException ex) {
            conversationId = null;
            add(new Paragraph("Conversation could not be loaded. Refresh this page to try again."));
        }
    }

    @Override protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        previousPollInterval = event.getUI().getPollInterval();
        event.getUI().setPollInterval(3000);
        poll = event.getUI().addPollListener(ignored -> refresh());
    }

    @Override protected void onDetach(DetachEvent event) {
        if (poll != null) poll.remove();
        event.getUI().setPollInterval(previousPollInterval);
        draft.clear();
        super.onDetach(event);
    }

    private void updateState(MessagingService.Chat chat) {
        state.setText(chat.closed() ? "This conversation is closed. You can still read its messages." : "");
        state.setVisible(chat.closed());
        draft.setEnabled(!chat.closed());
        send.setEnabled(!chat.closed());
        confirm.setVisible(chat.provider() && !chat.closed());
        confirm.setEnabled(!chat.closed());
    }

    private void refresh() {
        if (conversationId == null) return;
        try {
            var chat = service.getChat(conversationId, lastId);
            append(chat.messages());
            updateState(chat);
        } catch (MessagingBlockedException ex) { blocked(ex); }
        catch (AccessDeniedException ex) { denied(); }
        catch (DataAccessException ex) { feedback.setText("New messages could not be loaded. Select Refresh to retry."); }
    }

    private void send() {
        if (!draft.isEnabled() || draft.getValue().isBlank()) {
            send.setEnabled(draft.isEnabled());
            return;
        }
        send.setEnabled(false);
        try {
            service.sendMessage(conversationId, draft.getValue());
            draft.clear();
            // Enter can arrive before the usual field synchronization. Explicitly clear the
            // browser too, even when Flow coalesces the server value back to its original empty value.
            draft.getElement().executeJs("this.value = '';");
            draft.focus();
            feedback.setText("");
            refresh();
        } catch (MessagingBlockedException ex) { blocked(ex); }
        catch (AccessDeniedException ex) { denied(); }
        catch (IllegalArgumentException ex) { feedback.setText(ex.getMessage()); }
        catch (DataAccessException ex) { feedback.setText("The message could not be sent. Your text is still here; select Send to retry."); }
        finally { send.setEnabled(draft.isEnabled()); }
    }

    private void confirmSale() {
        try {
            sales.confirmSaleAndClose(conversationId);
            feedback.setText("Sale confirmed. The buyer can now leave a review on your profile.");
            refresh();
        } catch (MessagingBlockedException ex) { blocked(ex); }
        catch (AccessDeniedException ex) { denied(); }
        catch (IllegalArgumentException ex) { feedback.setText(ex.getMessage()); }
        catch (DataAccessException ex) { feedback.setText("The sale could not be confirmed. Try again."); }
        finally { confirm.setEnabled(draft.isEnabled()); }
    }

    private void loadOlder() {
        try {
            var messages = service.getOlderMessages(conversationId, firstId);
            for (int i = messages.size() - 1; i >= 0; i--) history.addComponentAtIndex(0, message(messages.get(i)));
            updateTimestampGroups();
            if (!messages.isEmpty()) firstId = messages.getFirst().id();
            older.setVisible(messages.size() == 100);
        } catch (MessagingBlockedException ex) { blocked(ex); }
        catch (AccessDeniedException ex) { denied(); }
        catch (DataAccessException ex) { feedback.setText("Older messages could not be loaded. Try again."); }
    }

    private void append(List<MessageSummary> messages) {
        boolean added = false;
        for (var item : messages) {
            if (lastId != null && item.id() <= lastId) continue;
            history.add(message(item));
            if (firstId == null) firstId = item.id();
            lastId = item.id();
            added = true;
        }
        if (added) {
            updateTimestampGroups();
            history.getElement().executeJs("requestAnimationFrame(() => { if (!this.dataset.scrolled || this.scrollHeight - this.scrollTop - this.clientHeight < 300) { this.scrollTop = this.scrollHeight; } this.dataset.scrolled = 'true'; })");
        }
    }

    private void updateTimestampGroups() {
        MessageTime previous = null;
        for (var current : messageTimes.values()) {
            if (previous != null) {
                previous.timestamp().setVisible(Duration.between(previous.sentAt(), current.sentAt())
                        .compareTo(Duration.ofMinutes(5)) > 0);
            }
            previous = current;
        }
        if (previous != null) previous.timestamp().setVisible(true);
    }

    private Component message(MessageSummary item) {
        Div card = new Div();
        boolean outgoing = viewerIsProvider == item.senderId().equals(providerId);
        card.addClassNames("mav-chat-message", outgoing ? "mav-chat-outgoing" : "mav-chat-incoming");
        var time = DateTimeFormatter.ofPattern("MMM d, uuuu HH:mm 'UTC'").withZone(ZoneOffset.UTC).format(item.sentAt());
        Paragraph body = new Paragraph(item.body());
        body.addClassName("mav-chat-bubble");
        body.getElement().setAttribute("title", item.senderName() + " · " + time);
        Span timestamp = new Span(time);
        timestamp.addClassName("mav-chat-time");
        messageTimes.put(item.id(), new MessageTime(item.sentAt(), timestamp));
        card.add(body, timestamp);
        return card;
    }

    private void blocked(MessagingBlockedException ex) {
        denied();
        Notification.show(ex.getMessage(), 7000, Notification.Position.MIDDLE);
        getUI().ifPresent(ui -> ui.navigate("listings/" + ex.getListingId()));
    }

    private void denied() {
        conversationId = null;
        history.removeAll();
        messageTimes.clear();
        draft.clear();
        draft.setEnabled(false);
        send.setEnabled(false);
        confirm.setVisible(false);
        older.setVisible(false);
        feedback.setText("Conversation not available.");
    }
}

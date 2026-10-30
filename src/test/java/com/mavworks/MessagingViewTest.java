package com.mavworks;

import com.mavworks.dto.MessageSummary;
import com.mavworks.messaging.ConversationView;
import com.mavworks.service.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.*;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessagingViewTest {
    @Test void keyboardSendUsesCurrentInputEvenBeforeFieldChangeIsSynchronized() {
        var service = mock(MessagingService.class);
        when(service.getChat(3L, null)).thenReturn(chat(false, List.of()));
        var view = view(service);
        var field = draft(view);
        field.setValue("Old input");
        var payload = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        payload.put("event.target.value", "Latest message");
        // Flow includes the browser-evaluated listener filter in a keyboard event payload.
        payload.put("event.key === 'Enter' && !event.shiftKey && !event.ctrlKey && !event.altKey "
                + "&& !event.metaKey && !event.isComposing && (event.preventDefault() || true) && !event.repeat", true);
        field.getElement().getNode().getFeature(com.vaadin.flow.internal.nodefeature.ElementListenerMap.class)
                .fireEvent(new com.vaadin.flow.dom.DomEvent(field.getElement(), "keydown", payload));
        verify(service).sendMessage(3L, "Latest message");
        assertTrue(field.getValue().isEmpty());
    }

    @Test void timestampsAppearOnlyAtGroupEndsAndMoveWhenNewMessagesArrive() {
        var service = mock(MessagingService.class);
        Instant start = Instant.parse("2026-10-29T12:00:00Z");
        var first = new MessageSummary(1L, 1L, "buyer", "First", start);
        var second = new MessageSummary(2L, 2L, "provider", "Reply", start.plusSeconds(60));
        var third = new MessageSummary(3L, 1L, "buyer", "Later", start.plusSeconds(600));
        var fourth = new MessageSummary(4L, 2L, "provider", "Another reply", start.plusSeconds(660));
        when(service.getChat(3L, null)).thenReturn(chat(false, List.of(first, second, third)));
        when(service.getChat(3L, 3L)).thenReturn(chat(false, List.of(fourth)));
        var view = view(service);
        var times = components(view).filter(c -> c instanceof com.vaadin.flow.component.html.Span s
                && s.hasClassName("mav-chat-time")).toList();
        assertFalse(times.get(0).isVisible());
        assertTrue(times.get(1).isVisible());
        assertTrue(times.get(2).isVisible());
        button(view, "Refresh").click();
        assertFalse(times.get(2).isVisible());
        assertEquals(2, components(view).filter(c -> c instanceof com.vaadin.flow.component.html.Span s
                && s.hasClassName("mav-chat-time") && s.isVisible()).count());
    }

    @Test void failedSendPreservesDraftAndRetryPublishesMessage() {
        var service = mock(MessagingService.class);
        var sent = new MessageSummary(8L, 1L, "buyer", "Hello", Instant.now());
        when(service.getChat(3L, null)).thenReturn(chat(false, List.of()), chat(false, List.of(sent)));
        when(service.sendMessage(3L, "Hello")).thenThrow(new DataAccessResourceFailureException("unavailable")).thenReturn(8L);
        var view = view(service);
        var draft = draft(view);
        draft.setValue("Hello");
        button(view, "Send").click();
        assertEquals("Hello", draft.getValue());
        assertTrue(button(view, "Send").isEnabled());
        button(view, "Send").click();
        assertEquals("", draft.getValue());
        verify(service, times(2)).sendMessage(3L, "Hello");
        assertTrue(components(view).anyMatch(c -> c instanceof Paragraph p && p.getText().equals("Hello")));
    }

    @Test void refreshingPreservesDraftAndLeavingDiscardsIt() {
        var service = mock(MessagingService.class);
        when(service.getChat(3L, null)).thenReturn(chat(false, List.of()));
        var view = view(service);
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            ui.add(view);
            var draft = draft(view);
            draft.setValue("Not sent");
            button(view, "Refresh").click();
            assertEquals("Not sent", draft.getValue());
            assertEquals(3000, ui.getPollInterval());
            ui.remove(view);
            assertEquals("", draft.getValue());
            assertEquals(-1, ui.getPollInterval());
            verify(service, never()).sendMessage(any(), any());
        } finally { UI.setCurrent(null); }
    }

    @Test void closedConversationIsReadOnlyAndUnauthorizedHistoryIsNotRendered() {
        var service = mock(MessagingService.class);
        when(service.getChat(3L, null)).thenReturn(chat(true, List.of()));
        var view = view(service);
        assertFalse(draft(view).isEnabled());
        assertFalse(button(view, "Send").isEnabled());
        assertFalse(button(view, "Confirm sale and close").isVisible());
        when(service.getChat(3L, null)).thenThrow(new AccessDeniedException("Not a participant"));
        var denied = view(service);
        assertFalse(components(denied).anyMatch(TextArea.class::isInstance));
    }

    private MessagingService.Chat chat(boolean closed, List<MessageSummary> messages) {
        return new MessagingService.Chat(3L, 10L, "Book", 2L, "provider", false, closed, messages);
    }
    private ConversationView view(MessagingService service) {
        var view = new ConversationView(service, mock(ConversationService.class));
        var event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters("conversationId", "3"));
        view.beforeEnter(event);
        return view;
    }
    private TextArea draft(Component root) {
        return components(root).filter(TextArea.class::isInstance).map(TextArea.class::cast).findFirst().orElseThrow();
    }
    private Button button(Component root, String label) {
        return components(root).filter(c -> c instanceof Button b && (b.getText().equals(label)
                || label.equals(b.getElement().getAttribute("aria-label")))).map(Button.class::cast).findFirst().orElseThrow();
    }
    private Stream<Component> components(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::components));
    }
}

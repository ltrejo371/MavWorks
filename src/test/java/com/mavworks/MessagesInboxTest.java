package com.mavworks;

import com.mavworks.messaging.MessagesView;
import com.mavworks.service.*;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.router.BeforeEnterEvent;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessagesInboxTest {
    @Test void pollingUpdatesInboxPreservesUnchangedCardsAndStopsOnDetach() {
        var service = mock(MessagingService.class);
        var first = new MessagingService.ConversationSummary(1L, 10L, "Book", "buyer", false, null, null);
        var second = new MessagingService.ConversationSummary(2L, 11L, "Tutoring", "provider", false, null, null);
        when(service.getMyConversations()).thenReturn(List.of(first));
        var view = new MessagesView(service, mock(ImageStorageService.class));
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            view.beforeEnter(mock(BeforeEnterEvent.class));
            ui.add(view);
            assertEquals(5000, ui.getPollInterval());
            var link = links(view).getFirst();
            poll(ui);
            assertSame(link, links(view).getFirst());
            when(service.getMyConversations()).thenReturn(List.of(second, first));
            poll(ui);
            assertEquals(List.of("messages/2", "messages/1"), links(view).stream().map(Anchor::getHref).toList());
            ui.remove(view);
            assertEquals(-1, ui.getPollInterval());
            clearInvocations(service);
            poll(ui);
            verifyNoInteractions(service);
        } finally { UI.setCurrent(null); }
    }

    @Test void failedPollKeepsExistingConversationsAndNextPollRecovers() {
        var service = mock(MessagingService.class);
        var item = new MessagingService.ConversationSummary(1L, 10L, "Book", "buyer", false, null, null);
        when(service.getMyConversations()).thenReturn(List.of(item))
                .thenThrow(new DataAccessResourceFailureException("offline")).thenReturn(List.of(item));
        var view = new MessagesView(service, mock(ImageStorageService.class));
        var ui = new UI();
        UI.setCurrent(ui);
        try {
            view.beforeEnter(mock(BeforeEnterEvent.class));
            ui.add(view);
            poll(ui);
            assertEquals(1, links(view).size());
            assertTrue(components(view).anyMatch(c -> c instanceof Paragraph p && p.isVisible()
                    && p.getText().contains("Retrying automatically")));
            poll(ui);
            assertFalse(components(view).anyMatch(c -> c instanceof Paragraph p && p.isVisible()
                    && p.getText().contains("Retrying automatically")));
            verify(service, never()).markConversationRead(any(), any());
            ui.remove(view);
        } finally { UI.setCurrent(null); }
    }
    private void poll(UI ui) { ComponentUtil.fireEvent(ui, new PollEvent(ui, true)); }
    private List<Anchor> links(Component root) {
        return components(root).filter(Anchor.class::isInstance).map(Anchor.class::cast).toList();
    }
    private Stream<Component> components(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::components));
    }
}

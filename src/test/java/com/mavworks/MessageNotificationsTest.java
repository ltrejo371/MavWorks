package com.mavworks;

import com.mavworks.messaging.MessageNotifications;
import com.mavworks.service.MessagingService;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageNotificationsTest {
    @Test void badgeLinksPollingAndReadUpdatesWorkWithoutAcknowledgingMessages() {
        var service = mock(MessagingService.class);
        when(service.getUnreadNotifications()).thenReturn(List.of(
                new MessagingService.UnreadNotification(7L, "Textbook", "buyer", 2, 10L)));
        var ui = new UI(); UI.setCurrent(ui);
        try {
            var notifications = new MessageNotifications(service);
            ui.add(notifications);
            assertEquals(5000, ui.getPollInterval());
            assertTrue(components(notifications).anyMatch(c -> c instanceof Button b && b.getText().equals("2")));
            assertTrue(components(notifications).anyMatch(c -> c instanceof Anchor a && a.getHref().equals("messages/7")));
            verify(service, never()).markConversationRead(any(), any());
            when(service.getUnreadNotifications()).thenReturn(List.of());
            notifications.refresh();
            assertTrue(components(notifications).anyMatch(c -> c instanceof Button b && b.getText().equals("0")));
            assertFalse(components(notifications).anyMatch(c -> c instanceof Anchor a && a.getHref().equals("messages/7")));
            ui.remove(notifications);
            assertEquals(-1, ui.getPollInterval());
        } finally { UI.setCurrent(null); }
    }
    private Stream<Component> components(Component c) {
        return Stream.concat(Stream.of(c), c.getChildren().flatMap(this::components));
    }
}

package com.mavworks;

import com.mavworks.base.ui.ModerationNoticeDialog;
import com.mavworks.service.ModerationNoticeService.Notice;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ModerationNoticeDialogTest {
    @BeforeEach void setup() { UI.setCurrent(new UI()); }
    @AfterEach void cleanup() { UI.setCurrent(null); }

    @Test void warningShowsNumberAndOffenseAndRequiresAcknowledgment() {
        var acknowledged = new AtomicBoolean();
        var dialog = new ModerationNoticeDialog(List.of(new Notice(1L, "Spam", "Repeated spam", 2, false)),
                false, "mavworks0@gmail.com", () -> acknowledged.set(true));
        dialog.open();
        assertFalse(dialog.isCloseOnEsc());
        assertFalse(dialog.isCloseOnOutsideClick());
        assertTrue(dialog.getChildren().anyMatch(c -> c instanceof H3 h && h.getText().equals("Warning #2 — Spam")));
        assertTrue(dialog.getChildren().anyMatch(c -> c instanceof Paragraph p && p.getText().equals("Repeated spam")));
        dialog.getFooter().getElement().getChildren().map(element -> element.getComponent().orElse(null))
                .filter(Button.class::isInstance).map(Button.class::cast).findFirst().orElseThrow().click();
        assertTrue(acknowledged.get());
        assertFalse(dialog.isOpened());
    }

    @Test void banIncludesAppealEmailAndNumber() {
        var dialog = new ModerationNoticeDialog(List.of(new Notice(1L, "Fraud", "Payment fraud", 1, true)),
                true, "mavworks0@gmail.com", () -> {});
        assertEquals("Your account has been banned", dialog.getHeaderTitle());
        assertTrue(dialog.getChildren().anyMatch(c -> c instanceof Anchor a
                && a.getHref().equals("mailto:mavworks0@gmail.com") && a.getText().equals("mavworks0@gmail.com")));
    }
}

package com.mavworks.base.ui;

import com.mavworks.service.BlockService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserOptionsTest {
    private UI ui;
    @BeforeEach void attachUI() { ui = new UI(); UI.setCurrent(ui); }
    @AfterEach void clearUI() { UI.setCurrent(null); }

    @Test void cancelNeverWritesAndReturnsToPreviousPage() {
        var service = mock(BlockService.class);
        var refresh = new AtomicBoolean();
        var dialog = UserOptions.confirmation(2L, service, () -> refresh.set(true));
        dialog.open();
        verifyNoInteractions(service);
        button(dialog, "Cancel").click();
        assertFalse(dialog.isOpened());
        assertFalse(refresh.get());
        verifyNoInteractions(service);
    }

    @Test void failedSaveStaysOpenAndRetryCanSucceed() {
        var service = mock(BlockService.class);
        when(service.blockUser(2L)).thenThrow(new DataAccessResourceFailureException("offline"))
                .thenReturn(BlockService.Result.BLOCKED);
        var refresh = new AtomicBoolean();
        var dialog = UserOptions.confirmation(2L, service, () -> refresh.set(true));
        dialog.open();
        button(dialog, "Confirm block").click();
        assertTrue(dialog.isOpened());
        assertFalse(refresh.get());
        assertTrue(dialog.getChildren().anyMatch(c -> c instanceof Paragraph p && p.getText().contains("could not be blocked")));
        button(dialog, "Try again").click();
        assertFalse(dialog.isOpened());
        assertTrue(refresh.get());
        verify(service, times(2)).blockUser(2L);
    }

    @Test void alreadyBlockedClosesConfirmationAndReturnsToPage() {
        var service = mock(BlockService.class);
        when(service.blockUser(2L)).thenReturn(BlockService.Result.ALREADY_BLOCKED);
        var refresh = new AtomicBoolean();
        var dialog = UserOptions.confirmation(2L, service, () -> refresh.set(true));
        dialog.open();
        button(dialog, "Confirm block").click();
        assertFalse(dialog.isOpened());
        assertTrue(refresh.get());
        verify(service).blockUser(2L);
    }

    private Button button(Dialog dialog, String label) {
        return dialog.getFooter().getElement().getChildren().map(e -> e.getComponent().orElseThrow())
                .filter(c -> c instanceof Button b && b.getText().equals(label)).map(Button.class::cast)
                .findFirst().orElseThrow();
    }
}

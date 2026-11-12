package com.mavworks;

import com.mavworks.dashboard.EditAccountDialog;
import com.mavworks.service.*;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.*;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EditAccountDialogTest {
    private UI ui;
    @BeforeEach void setup() { ui = new UI(); UI.setCurrent(ui); }
    @AfterEach void clear() { UI.setCurrent(null); }
    AccountDashboardService.DashboardData data() {
        return new AccountDashboardService.DashboardData("owner", "owner@mavs.uta.edu", "Owner", "Old bio",
                "Email me at contact@example.com", List.of(), List.of(), 1L, false, List.of(), List.of());
    }
    @Test void currentFieldsAreShownAndCancelDoesNotSave() {
        var service = mock(AccountEditingService.class);
        var dialog = new EditAccountDialog(data(), service, mock(LocalImageStorageService.class), () -> fail("Unexpected save"));
        dialog.open();
        assertEquals("owner", username(dialog).getValue());
        assertEquals("Old bio", bio(dialog).getValue());
        assertEquals("Email me at contact@example.com", contact(dialog).getValue());
        contact(dialog).setValue("Unsent contact");
        bio(dialog).setValue("Unsent");
        button(dialog, "Cancel").click();
        assertFalse(dialog.isOpened());
        verifyNoInteractions(service);
    }
    @Test void failedSaveRetainsInputAndSuccessfulRetryRefreshesDashboard() {
        var service = mock(AccountEditingService.class);
        var saved = mock(Runnable.class);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("offline")).doNothing()
                .when(service).save("newname", "New bio", null, "New contact");
        var dialog = new EditAccountDialog(data(), service, mock(LocalImageStorageService.class), saved);
        dialog.open(); username(dialog).setValue("newname"); bio(dialog).setValue("New bio");
        contact(dialog).setValue("New contact");
        button(dialog, "Save").click();
        assertTrue(dialog.isOpened()); assertEquals("New bio", bio(dialog).getValue());
        assertEquals("New contact", contact(dialog).getValue());
        assertTrue(button(dialog, "Save").isEnabled()); verifyNoInteractions(saved);
        button(dialog, "Save").click();
        assertFalse(dialog.isOpened()); verify(saved).run();
    }
    TextField username(EditAccountDialog dialog) {
        return (TextField) dialog.getChildren().filter(TextField.class::isInstance).findFirst().orElseThrow();
    }
    TextArea bio(EditAccountDialog dialog) {
        return (TextArea) dialog.getChildren().filter(TextArea.class::isInstance).findFirst().orElseThrow();
    }
    TextArea contact(EditAccountDialog dialog) {
        return dialog.getChildren().filter(c -> c instanceof TextArea a && a.getLabel().equals("Contact information"))
                .map(TextArea.class::cast).findFirst().orElseThrow();
    }
    Button button(EditAccountDialog dialog, String label) {
        return dialog.getFooter().getElement().getChildren().map(e -> e.getComponent().orElseThrow())
                .filter(c -> c instanceof Button b && b.getText().equals(label)).map(Button.class::cast).findFirst().orElseThrow();
    }
}

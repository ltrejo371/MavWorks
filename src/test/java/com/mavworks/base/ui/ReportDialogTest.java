package com.mavworks.base.ui;

import com.mavworks.model.ReportCategory;
import com.mavworks.service.ReportService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.TextArea;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportDialogTest {
    private UI ui;
    @BeforeEach void setup() { ui = new UI(); UI.setCurrent(ui); }
    @AfterEach void clear() { UI.setCurrent(null); }

    @Test void submitRequiresCategoryAndCancelDoesNotSave() {
        var reports = mock(ReportService.class);
        var dialog = new ReportDialog(2L, reports);
        dialog.open();
        assertFalse(button(dialog, "Submit").isEnabled());
        category(dialog).setValue(ReportCategory.SPAM);
        assertTrue(button(dialog, "Submit").isEnabled());
        reason(dialog).setValue("Unsent explanation");
        button(dialog, "Cancel").click();
        assertFalse(dialog.isOpened());
        assertNull(category(dialog).getValue());
        assertTrue(reason(dialog).getValue().isEmpty());
        verifyNoInteractions(reports);
    }

    @Test void closingWithoutSubmittingDiscardsInput() {
        var reports = mock(ReportService.class);
        var dialog = new ReportDialog(2L, reports);
        dialog.open();
        category(dialog).setValue(ReportCategory.OTHER);
        reason(dialog).setValue("Not submitted");
        dialog.close();
        assertTrue(reason(dialog).getValue().isEmpty());
        verifyNoInteractions(reports);
    }

    @Test void failedSaveRetainsFormAndRetrySubmitsToAdminQueue() {
        var reports = mock(ReportService.class);
        when(reports.reportUser(2L, ReportCategory.HARASSMENT, "Repeated threats"))
                .thenThrow(new DataAccessResourceFailureException("offline")).thenReturn(9L);
        var dialog = new ReportDialog(2L, reports);
        dialog.open();
        category(dialog).setValue(ReportCategory.HARASSMENT);
        reason(dialog).setValue("Repeated threats");
        button(dialog, "Submit").click();
        assertTrue(dialog.isOpened());
        assertEquals(ReportCategory.HARASSMENT, category(dialog).getValue());
        assertEquals("Repeated threats", reason(dialog).getValue());
        button(dialog, "Try again").click();
        assertFalse(dialog.isOpened());
        verify(reports, times(2)).reportUser(2L, ReportCategory.HARASSMENT, "Repeated threats");
    }

    @SuppressWarnings("unchecked")
    private ComboBox<ReportCategory> category(ReportDialog dialog) {
        return (ComboBox<ReportCategory>) dialog.getChildren().filter(ComboBox.class::isInstance).findFirst().orElseThrow();
    }
    private TextArea reason(ReportDialog dialog) {
        return (TextArea) dialog.getChildren().filter(TextArea.class::isInstance).findFirst().orElseThrow();
    }
    private Button button(ReportDialog dialog, String label) {
        return dialog.getFooter().getElement().getChildren().map(e -> e.getComponent().orElseThrow())
                .filter(c -> c instanceof Button b && b.getText().equals(label)).map(Button.class::cast).findFirst().orElseThrow();
    }
}

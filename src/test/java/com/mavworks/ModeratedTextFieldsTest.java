package com.mavworks;

import com.mavworks.base.ui.ModeratedTextFields;
import com.mavworks.listing.CreateListingView;
import com.mavworks.service.ListingService;
import com.mavworks.service.ImageStorageService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModeratedTextFieldsTest {
    @Test void highlightsEveryOffendingFieldAndClearsOnEdit() {
        var title = new TextField();
        var description = new TextArea();
        var fields = new ModeratedTextFields();
        fields.add("Listing title", title);
        fields.add("Listing description", description);
        title.setValue("shit");
        description.setValue("fuck you");
        assertFalse(fields.validate());
        assertTrue(title.isInvalid());
        assertTrue(description.isInvalid());
        assertTrue(title.getErrorMessage().startsWith("Listing title"));
        assertTrue(description.getErrorMessage().startsWith("Listing description"));
        assertEquals("fuck you", description.getValue());
        title.setValue("Tutoring");
        assertFalse(title.isInvalid());
        assertTrue(description.isInvalid());
        description.setValue("Math help");
        assertFalse(description.isInvalid());
        assertTrue(fields.validate());
    }

    @Test void listingSubmitHighlightsFieldsBeforeStorageOrSave() {
        var service = mock(ListingService.class);
        var storage = mock(ImageStorageService.class);
        var view = new CreateListingView(service, storage);
        var name = components(view).filter(TextField.class::isInstance).map(TextField.class::cast)
                .filter(field -> field.getLabel().equals("Name")).findFirst().orElseThrow();
        var description = components(view).filter(TextArea.class::isInstance).map(TextArea.class::cast)
                .findFirst().orElseThrow();
        name.setValue("shit");
        description.setValue("fuck you");
        components(view).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText().equals("Done")).findFirst().orElseThrow().click();
        assertTrue(name.isInvalid());
        assertTrue(description.isInvalid());
        verifyNoInteractions(service, storage);
    }

    private Stream<Component> components(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::components));
    }
}

package com.mavworks;

import com.mavworks.admin.ListingsModerationView;
import com.mavworks.model.ListingStatus;
import com.mavworks.service.ListingManagementService;
import com.mavworks.service.ListingManagementService.ModerationFilter;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.router.BeforeEnterEvent;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListingsModerationViewTest {
    private final ListingManagementService service = mock(ListingManagementService.class);
    private UI ui;
    @BeforeEach void setup() { ui = new UI(); UI.setCurrent(ui); }
    @AfterEach void cleanup() { UI.setCurrent(null); }
    private ListingsModerationView view(ListingStatus status) {
        var item = new ListingManagementService.ModerationListing(7L, "Old textbook", "owner", status,
                Instant.now().minusSeconds(3600), Instant.now().minusSeconds(60));
        when(service.moderationQueue(any(ModerationFilter.class), anyInt())).thenReturn(new PageImpl<>(List.of(item)));
        var view = new ListingsModerationView(service); ui.add(view);
        view.beforeEnter(mock(BeforeEnterEvent.class));
        return view;
    }
    @Test void removalRequiresConfirmationAndCancelDoesNothing() {
        var view = view(ListingStatus.EXPIRED);
        verify(service).moderationQueue(ModerationFilter.OUTDATED_INACTIVE, 0);
        button(view, "Remove listing").click();
        verify(service, never()).remove(any());
        button(view, "Cancel").click();
        verify(service, never()).remove(any());
        button(view, "Remove listing").click();
        when(service.moderationQueue(any(ModerationFilter.class), anyInt())).thenReturn(Page.empty());
        button(view, "Confirm removal").click();
        verify(service).remove(7L);
        assertFalse(components(view).anyMatch(c -> c instanceof Button b && b.getText().equals("Remove listing")));
    }
    @Test void failedRemovalAllowsRetryAndRemovedRowsHaveNoRemoveAction() {
        var view = view(ListingStatus.WITHDRAWN);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("offline")).doNothing().when(service).remove(7L);
        button(view, "Remove listing").click();
        button(view, "Confirm removal").click();
        assertTrue(button(view, "Confirm removal").isEnabled());
        button(view, "Confirm removal").click();
        verify(service, times(2)).remove(7L);
        var removed = view(ListingStatus.REMOVED);
        assertFalse(components(removed).anyMatch(c -> c instanceof Button b && b.getText().equals("Remove listing")));
    }
    @Test void filterChangesRestartPaginationAndRouteIsAdminOnly() {
        var view = view(ListingStatus.EXPIRED);
        @SuppressWarnings("unchecked")
        var filter = (ComboBox<ModerationFilter>) components(view).filter(ComboBox.class::isInstance).findFirst().orElseThrow();
        filter.setValue(ModerationFilter.REMOVED);
        verify(service).moderationQueue(ModerationFilter.REMOVED, 0);
        assertArrayEquals(new String[]{"ADMIN"}, ListingsModerationView.class.getAnnotation(jakarta.annotation.security.RolesAllowed.class).value());
    }
    private Button button(Component root, String text) {
        return components(root).filter(c -> c instanceof Button b && b.getText().equals(text)).map(Button.class::cast).findFirst().orElseThrow();
    }
    private Stream<Component> components(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::components));
    }
}

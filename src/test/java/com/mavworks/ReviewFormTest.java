package com.mavworks;

import com.mavworks.dto.*;
import com.mavworks.profile.ProfileView;
import com.mavworks.service.ReviewService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewFormTest {
    @Test void failedSubmissionPreservesInputsAndCanBeRetried() {
        var service = mock(ReviewService.class);
        var view = view(service);
        var item = new ReviewableListing(10L, "Tutoring");
        when(service.getReviewableListings(2L)).thenReturn(List.of(item));
        when(service.leaveReview(eq(2L), any())).thenThrow(new DataAccessResourceFailureException("unavailable")).thenReturn(5L);
        button(view, "Review").click();
        @SuppressWarnings("unchecked") var listing = (ComboBox<ReviewableListing>) components(view)
                .filter(c -> c instanceof ComboBox<?> box && box.getLabel().equals("Listing")).findFirst().orElseThrow();
        @SuppressWarnings("unchecked") var rating = (ComboBox<Integer>) components(view)
                .filter(c -> c instanceof ComboBox<?> box && box.getLabel().startsWith("Rating")).findFirst().orElseThrow();
        var comment = (TextArea) components(view).filter(TextArea.class::isInstance).findFirst().orElseThrow();
        button(view, "Submit").click();
        assertTrue(listing.isInvalid());
        assertTrue(rating.isInvalid());
        verify(service, never()).leaveReview(any(), any());
        listing.setValue(item);
        rating.setValue(4);
        comment.setValue("Helpful tutor");
        button(view, "Submit").click();
        assertEquals(item, listing.getValue());
        assertEquals(4, rating.getValue());
        assertEquals("Helpful tutor", comment.getValue());
        button(view, "Try again").click();
        verify(service, times(2)).leaveReview(2L, new CreateReviewRequest(10L, 4, "Helpful tutor"));
        assertTrue(components(view).anyMatch(c -> c instanceof Paragraph p && p.getText().equals("Your review was published.")));
    }

    @Test void noQualifyingConversationKeepsUserOnProfileWithoutForm() {
        var service = mock(ReviewService.class);
        var view = view(service);
        when(service.getReviewableListings(2L)).thenReturn(List.of());
        button(view, "Review").click();
        assertTrue(components(view).anyMatch(c -> c instanceof Paragraph p && p.getText().contains("completed sale")));
        assertFalse(components(view).anyMatch(TextArea.class::isInstance));
        verify(service, never()).leaveReview(any(), any());
    }

    private ProfileView view(ReviewService service) {
        when(service.getProviderProfile(2L)).thenReturn(Optional.of(new ReviewService.ProviderProfile(
                new PublicProfileData(2L, "provider", "Provider", "", null, ""), List.of())));
        var view = new ProfileView(service, mock(com.mavworks.service.BlockService.class), mock(com.mavworks.service.ReportService.class));
        var event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters("userId", "2"));
        view.beforeEnter(event);
        return view;
    }
    private Button button(Component root, String text) {
        return components(root).filter(c -> c instanceof Button b && b.getText().equals(text)).map(Button.class::cast).findFirst().orElseThrow();
    }
    private Stream<Component> components(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::components));
    }
}

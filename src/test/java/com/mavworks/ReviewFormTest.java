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

    @Test void providerCanRetryReplyWithoutLosingTextAndIgnoreReview() {
        var service = mock(ReviewService.class);
        when(service.canReplyToProfile(2L)).thenReturn(true);
        var view = view(service);
        when(service.getProviderProfile(2L)).thenReturn(Optional.of(profileWithReviews()));
        enter(view);
        button(view, "Reply").click();
        var text = (TextArea) components(view).filter(TextArea.class::isInstance).findFirst().orElseThrow();
        text.setValue("Thanks for your feedback");
        doThrow(new DataAccessResourceFailureException("unavailable")).doNothing()
                .when(service).replyToReview(7L, "Thanks for your feedback");
        button(view, "Confirm reply").click();
        assertEquals("Thanks for your feedback", text.getValue());
        assertTrue(components(view).anyMatch(c -> c instanceof Paragraph p && p.getText().contains("could not be saved")));
        button(view, "Confirm reply").click();
        verify(service, times(2)).replyToReview(7L, "Thanks for your feedback");
        button(view, "Ignore").click();
        verify(service).ignoreReview(7L);
    }

    @Test void visitorsHaveNoReplyControlsAndCanSortReviewsAndRetryLoading() {
        var service = mock(ReviewService.class);
        var view = view(service);
        when(service.getProviderProfile(2L)).thenThrow(new DataAccessResourceFailureException("unavailable"))
                .thenReturn(Optional.of(profileWithReviews()));
        enter(view);
        button(view, "Try again").click();
        assertFalse(components(view).anyMatch(c -> c instanceof Button b && b.getText().equals("Reply")));
        @SuppressWarnings("unchecked") var sort = (ComboBox<String>) components(view)
                .filter(c -> c instanceof ComboBox<?> b && b.getLabel().equals("Sort reviews")).findFirst().orElseThrow();
        assertEquals("Newest first", sort.getValue());
        assertEquals(List.of("Recent review", "Older review"), reviewComments(view));
        sort.setValue("Highest rating");
        assertEquals(List.of("Older review", "Recent review"), reviewComments(view));
        sort.setValue("Lowest rating");
        assertEquals(List.of("Recent review", "Older review"), reviewComments(view));
    }

    private List<String> reviewComments(ProfileView view) {
        return components(view).filter(Paragraph.class::isInstance).map(Paragraph.class::cast)
                .map(Paragraph::getText).filter(t -> t.endsWith(" review")).toList();
    }

    private ReviewService.ProviderProfile profileWithReviews() {
        return new ReviewService.ProviderProfile(new PublicProfileData(2L, "provider", "Provider", "", null, ""),
                List.of(new ReviewSummary(7L, "buyer", 10L, "Tutoring", 2, "Recent review", java.time.Instant.now(), 3L, null),
                        new ReviewSummary(8L, "buyer", 11L, "Book", 5, "Older review", java.time.Instant.EPOCH, 3L, null)),
                List.of(), 3.5, 2);
    }

    private void enter(ProfileView view) {
        var event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters("userId", "2"));
        view.beforeEnter(event);
    }

    private ProfileView view(ReviewService service) {
        when(service.getProviderProfile(2L)).thenReturn(Optional.of(new ReviewService.ProviderProfile(
                new PublicProfileData(2L, "provider", "Provider", "", null, ""), List.of(), List.of(), null, 0)));
        var view = new ProfileView(service, mock(com.mavworks.service.BlockService.class), mock(com.mavworks.service.ReportService.class), mock(com.mavworks.service.LocalImageStorageService.class));
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

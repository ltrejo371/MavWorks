package com.mavworks.profile;

import com.mavworks.base.ui.ViewTitle;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import com.mavworks.dto.CreateReviewRequest;
import com.mavworks.dto.ReviewableListing;
import com.mavworks.service.ReviewService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;

@Route("profiles/:userId")
@PageTitle("Public Profile")
@PermitAll
public class ProfileView extends VerticalLayout implements BeforeEnterObserver {
    private final ReviewService service;
    private Long providerId;
    private final VerticalLayout form = new VerticalLayout();
    private final Paragraph feedback = new Paragraph();

    public ProfileView(ReviewService service) {
        this.service = service;
        setMaxWidth("48rem");
        form.setPadding(false);
        feedback.getElement().setAttribute("role", "status");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        feedback.setText("");
        try {
            providerId = Long.valueOf(event.getRouteParameters().get("userId").orElse(""));
            loadProfile();
        } catch (NumberFormatException ex) {
            removeAll();
            add(new Paragraph("Provider not found."));
        }
    }

    private void loadProfile() {
        removeAll();
        form.removeAll();
        form.setVisible(false);
        try {
            var result = service.getProviderProfile(providerId);
            if (result.isEmpty()) {
                add(new Paragraph("Provider not found."));
                return;
            }
            var data = result.get();
            var profile = data.profile();
            add(new ViewTitle(profile.username()));
            if (profile.name() != null && !profile.name().isBlank()) add(new Paragraph(profile.name()));
            if (profile.bio() != null && !profile.bio().isBlank()) add(new Paragraph(profile.bio()));
            if (profile.contactInformation() != null && !profile.contactInformation().isBlank()) {
                add(new Paragraph(profile.contactInformation()));
            }
            add(new Button("Review", event -> openReviewForm()), feedback, form, new H2("Reviews"));
            if (data.reviews().isEmpty()) add(new Paragraph("No reviews yet."));
            for (var review : data.reviews()) {
                VerticalLayout card = new VerticalLayout();
                card.setPadding(false);
                card.getStyle().set("border-bottom", "1px solid #d8dce2").set("padding-bottom", "1rem");
                card.add(new Paragraph(review.author() + " · " + review.rating() + "/5"),
                        new Paragraph("Listing: " + review.listingName()));
                if (review.comment() != null && !review.comment().isBlank()) {
                    Paragraph comment = new Paragraph(review.comment());
                    comment.getStyle().set("white-space", "pre-wrap");
                    card.add(comment);
                }
                add(card);
            }
        } catch (DataAccessException ex) {
            add(new Paragraph("The profile could not be loaded."),
                    new Button("Try again", event -> loadProfile()));
        }
    }

    private void openReviewForm() {
        // Repeated clicks should not discard a review the user is writing.
        if (form.isVisible()) return;
        feedback.setText("");
        try {
            var listings = service.getReviewableListings(providerId);
            if (listings.isEmpty()) {
                feedback.setText("A review can only be left after a completed sale. You have no completed-sale listings left to review with this provider.");
                return;
            }
            ComboBox<ReviewableListing> listing = new ComboBox<>("Listing");
            listing.setItems(listings);
            listing.setItemLabelGenerator(item -> item.name() + " (#" + item.id() + ")");
            listing.setRequiredIndicatorVisible(true);
            ComboBox<Integer> rating = new ComboBox<>("Rating (1–5)");
            rating.setItems(1, 2, 3, 4, 5);
            rating.setRequiredIndicatorVisible(true);
            TextArea comment = new TextArea("Comment (optional)");
            comment.setMaxLength(2000);
            comment.setHelperText("Up to 2000 characters. One review per listing; reviews cannot be edited yet.");
            comment.setWidthFull();
            Paragraph error = new Paragraph();
            error.getElement().setAttribute("role", "alert");
            Button submit = new Button("Submit");
            submit.setDisableOnClick(true);
            submit.addClickListener(event -> {
                error.setText("");
                listing.setInvalid(listing.getValue() == null);
                listing.setErrorMessage("Select the listing you are reviewing.");
                rating.setInvalid(rating.getValue() == null);
                rating.setErrorMessage("A rating from 1 to 5 is required.");
                if (listing.isInvalid() || rating.isInvalid()) {
                    submit.setEnabled(true);
                    return;
                }
                try {
                    service.leaveReview(providerId, new CreateReviewRequest(listing.getValue().id(),
                            rating.getValue(), comment.getValue()));
                    feedback.setText("Your review was published.");
                    loadProfile();
                } catch (IllegalArgumentException | AccessDeniedException ex) {
                    error.setText(ex.getMessage());
                } catch (DataAccessException ex) {
                    error.setText("Your review could not be submitted. Your entries are saved in this form; select Try again.");
                    submit.setText("Try again");
                } finally {
                    submit.setEnabled(true);
                }
            });
            form.removeAll();
            form.add(new H2("Leave a review"), listing, rating, comment, error, submit,
                    new Button("Cancel", event -> { form.removeAll(); form.setVisible(false); }));
            form.setVisible(true);
        } catch (AccessDeniedException ex) {
            feedback.setText("Sign in to leave a review.");
        } catch (DataAccessException ex) {
            feedback.setText("Review eligibility could not be checked. Select Review to try again.");
        }
    }
}

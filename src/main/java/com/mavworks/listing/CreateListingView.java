package com.mavworks.listing;

import com.mavworks.base.ui.ViewTitle;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import com.mavworks.dto.CreateListingRequest;
import com.mavworks.model.*;
import com.mavworks.service.*;
import com.mavworks.dashboard.DashboardView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Route("listings/new")
@PageTitle("Create Listing")
@PermitAll
public class CreateListingView extends VerticalLayout {
    public CreateListingView(ListingService listings, LocalImageStorageService storage) {
        addClassNames("mw-page", "mw-form-page");
        TextField name = new TextField("Name");
        TextField price = new TextField("Price ($)");

        ComboBox<ListingType> type = new ComboBox<>("Product or service");
        ComboBox<ListingCategory> category = new ComboBox<>("Category (optional)");

        TextArea description = new TextArea("Description");
        Paragraph errors = new Paragraph();
        errors.getElement().setAttribute("role", "alert");

        name.setRequiredIndicatorVisible(true);
        name.setMaxLength(255);

        price.setRequiredIndicatorVisible(true);
        type.setRequiredIndicatorVisible(true);

        type.setItems(ListingType.values());
        category.setItems(ListingCategory.values());

        category.setValue(ListingCategory.OTHER);

        description.setRequiredIndicatorVisible(true);
        description.setMaxLength(4000);
        description.setWidthFull();

        List<byte[]> selected = new ArrayList<>();
        var uploadsInProgress = new java.util.concurrent.atomic.AtomicInteger();
        VerticalLayout previews = new VerticalLayout();
        previews.setPadding(false);

        Button done = new Button("Done");
        done.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.PRIMARY);
        Upload upload = new Upload((UploadHandler) event -> {
            uploadsInProgress.incrementAndGet();
            try {
                event.getUI().accessSynchronously(() -> done.setEnabled(false));
                byte[] bytes = event.getInputStream().readNBytes(LocalImageStorageService.MAX_BYTES + 1);
                LocalImageStorageService.validate(bytes);
                event.getUI().accessSynchronously(() -> {
                    if (selected.size() >= 5) {
                        errors.setText("Select at most five images. Remove one before adding another.");
                        event.reject();
                        return;
                    }
                    selected.add(bytes);
                    Button remove = new Button("Remove " + event.getFileName());
                    remove.addClickListener(click -> { selected.remove(bytes); previews.remove(remove); });
                    previews.add(remove);
                    errors.setText("");
                });

            } catch (IllegalArgumentException ex) {
                event.reject(ex.getMessage());
                event.getUI().accessSynchronously(() -> errors.setText(ex.getMessage()));

            } catch (java.io.IOException ex) {
                event.reject("The image could not be uploaded. Please try again.");
                event.getUI().accessSynchronously(() -> errors.setText("The image could not be uploaded. Please try again."));

            } finally {
                uploadsInProgress.decrementAndGet();
            }
        }
        );

        upload.setMaxFiles(5);
        upload.setAcceptedFileTypes("image/jpeg", "image/png", ".jpg", ".jpeg", ".png");
        upload.setMaxFileSize(LocalImageStorageService.MAX_BYTES);
        upload.addFileRejectedListener(event -> errors.setText(event.getErrorMessage()));
        upload.addAllFinishedListener(event -> { done.setEnabled(true); upload.clearFileList(); });

        done.addClickListener(event -> {
            if (uploadsInProgress.get() > 0) {
                errors.setText("Wait for your images to finish uploading before selecting Done.");
                return;
            }
            errors.setText("");
            BigDecimal amount;
            try {
                amount = price.getValue().isBlank() ? null : new BigDecimal(price.getValue().trim());
            } catch (NumberFormatException ex) {
                errors.setText("Enter a valid price, such as 12.50.");
                return;
            }
            List<String> references = new ArrayList<>();
            boolean published = false;
            done.setEnabled(false);
            try {
                // Validate before writing any image files; authentication is enforced by the service.
                new User().createListing(new CreateListingRequest(name.getValue(), description.getValue(),
                        amount, type.getValue(), category.getValue(), List.of()));
                for (byte[] bytes : selected) {
                    references.add(storage.store(new ByteArrayInputStream(bytes), "image", null));
                }
                listings.createListing(new CreateListingRequest(name.getValue(), description.getValue(),
                        amount, type.getValue(), category.getValue(), references));
                published = true;
                getUI().ifPresent(ui -> ui.navigate(DashboardView.class));
            } catch (IllegalArgumentException ex) {
                errors.setText(ex.getMessage());
            } catch (org.springframework.security.access.AccessDeniedException ex) {
                errors.setText("Sign in before publishing a listing.");
            } catch (org.springframework.dao.DataAccessException | java.io.UncheckedIOException ex) {
                errors.setText("The listing could not be saved. Your information is still here; select Done to retry.");
            } finally {
                if (!published) {
                    for (String reference : references) {
                        try { storage.delete(reference); }
                        catch (RuntimeException cleanupFailure) {
                            org.slf4j.LoggerFactory.getLogger(CreateListingView.class)
                                    .warn("Could not clean up an unpublished listing image", cleanupFailure);
                        }
                    }
                }
                done.setEnabled(true);
            }
        });
        setMaxWidth("48rem");
        add(new ViewTitle("Create Listing"), name, price, type, category, description,
                new Paragraph("Images (optional): up to five JPEG or PNG files (5 MB and 20 megapixels each limit)"),
                upload, previews, errors, done);
    }
}

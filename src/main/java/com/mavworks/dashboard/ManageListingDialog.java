package com.mavworks.dashboard;

import com.mavworks.model.ListingCategory;
import com.mavworks.model.ListingStatus;
import com.mavworks.service.AccountDashboardService.OwnerListing;
import com.mavworks.service.ListingManagementService;
import com.mavworks.service.ImageStorageService;
import com.mavworks.service.ImageValidationService;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.IntegerField;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;

public class ManageListingDialog extends Dialog {
    private final Paragraph error = new Paragraph();

    public ManageListingDialog(
            OwnerListing listing,
            ListingManagementService service,
            ImageStorageService storage,
            Runnable refreshed) {

        setHeaderTitle("Manage listing");
        setWidth("36rem");
        setCloseOnOutsideClick(false);

        DateTimeFormatter dateFormat = DateTimeFormatter
                .ofPattern("MMM d, yyyy")
                .withZone(ZoneId.systemDefault());

        Paragraph status = new Paragraph(
                "Status: " + listing.status()
                        + " · Expires: " + dateFormat.format(listing.expiresAt()));

        TextField name = new TextField("Title");
        name.setValue(listing.name());
        name.setMaxLength(255);
        name.setRequiredIndicatorVisible(true);
        name.setWidthFull();

        TextArea description = new TextArea("Description");
        description.setValue(listing.description());
        description.setMaxLength(4000);
        description.setRequiredIndicatorVisible(true);
        description.setWidthFull();

        BigDecimalField price = new BigDecimalField("Price ($)");
        price.setValue(listing.price());
        price.setRequiredIndicatorVisible(true);
        price.setWidthFull();

        ComboBox<ListingCategory> category = new ComboBox<>("Category");
        category.setItems(ListingCategory.values());
        category.setItemLabelGenerator(value -> value.name().replace('_', ' '));
        category.setValue(listing.category());
        category.setWidthFull();

        IntegerField appointmentDuration = new IntegerField("Appointment length (minutes)");
        appointmentDuration.setValue(listing.appointmentDurationMinutes());
        appointmentDuration.setMin(5);
        appointmentDuration.setMax(480);
        appointmentDuration.setStep(5);
        appointmentDuration.setHelperText("Choose 5–480 minutes for this service.");
        appointmentDuration.setWidthFull();
        appointmentDuration.setVisible(listing.type() == com.mavworks.model.ListingType.SERVICE);

        boolean editable = listing.status() != ListingStatus.SOLD
                && listing.status() != ListingStatus.REMOVED;

        name.setReadOnly(!editable);
        description.setReadOnly(!editable);
        price.setReadOnly(!editable);
        category.setReadOnly(!editable);
        appointmentDuration.setReadOnly(!editable);

        error.getElement().setAttribute("role", "alert");

        List<byte[]> selected = new ArrayList<>();
        AtomicInteger uploadsInProgress = new AtomicInteger();
        VerticalLayout previews = new VerticalLayout();
        previews.setPadding(false);
        Button save = new Button("Save changes", event -> {
            if (uploadsInProgress.get() > 0) {
                error.setText("Wait for your images to finish uploading before saving.");
                return;
            }
            perform(() -> {
                List<String> references = new ArrayList<>();
                boolean saved = false;
                try {
                    for (byte[] bytes : selected) {
                        references.add(storage.store(new ByteArrayInputStream(bytes), "image", null));
                    }
                    service.updateDetails(listing.id(), name.getValue(), description.getValue(),
                            price.getValue(), category.getValue(), appointmentDuration.getValue(),
                            selected.isEmpty() ? null : references);
                    saved = true;
                } finally {
                    if (!saved) {
                        for (String reference : references) {
                            try { storage.delete(reference); }
                            catch (RuntimeException cleanupFailure) {
                                org.slf4j.LoggerFactory.getLogger(ManageListingDialog.class)
                                        .warn("Could not clean up an unsaved listing image", cleanupFailure);
                            }
                        }
                    }
                }
            }, refreshed);
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.setVisible(editable);

        Upload upload = new Upload((UploadHandler) event -> {
            uploadsInProgress.incrementAndGet();
            try {
                event.getUI().accessSynchronously(() -> save.setEnabled(false));
                byte[] bytes = event.getInputStream().readNBytes(ImageValidationService.MAX_BYTES + 1);
                String extension = ImageValidationService.validate(bytes);
                event.getUI().accessSynchronously(() -> {
                    if (selected.size() >= 5) {
                        error.setText("Select at most five images. Remove one before adding another.");
                        event.reject();
                        return;
                    }
                    selected.add(bytes);
                    Image preview = new Image("data:image/" + (extension.equals("jpg") ? "jpeg" : "png")
                            + ";base64," + Base64.getEncoder().encodeToString(bytes), event.getFileName());
                    preview.setWidth("120px");
                    preview.setHeight("90px");
                    preview.getStyle().set("object-fit", "cover");
                    Div item = new Div(preview);
                    item.add(new Button("Remove " + event.getFileName(), click -> {
                        selected.remove(bytes);
                        previews.remove(item);
                    }));
                    previews.add(item);
                    error.setText("");
                });
            } catch (IllegalArgumentException ex) {
                event.reject(ex.getMessage());
                event.getUI().accessSynchronously(() -> error.setText(ex.getMessage()));
            } catch (java.io.IOException ex) {
                event.reject("The image could not be uploaded. Please try again.");
                event.getUI().accessSynchronously(() -> error.setText("The image could not be uploaded. Please try again."));
            } finally {
                uploadsInProgress.decrementAndGet();
            }
        });
        upload.setMaxFiles(5);
        upload.setMaxFileSize(ImageValidationService.MAX_BYTES);
        upload.setAcceptedFileTypes("image/jpeg", "image/png", ".jpg", ".jpeg", ".png");
        upload.addFileRejectedListener(event -> error.setText(event.getErrorMessage()));
        upload.addAllFinishedListener(event -> { save.setEnabled(true); upload.clearFileList(); });
        VerticalLayout imageEditor = new VerticalLayout(new Paragraph(
                "Replace images (optional): select up to five JPEG or PNG files, each at most 5 MB and 20 megapixels. "
                + "Saving replaces all current images with your selection. Leave the selection empty to keep current images."),
                upload, previews);
        imageEditor.setPadding(false);
        imageEditor.setVisible(editable);

        Button withdraw = new Button("Withdraw listing", event ->
                perform(() -> service.withdraw(listing.id()), refreshed));
        withdraw.setVisible(editable
                && listing.status() != ListingStatus.WITHDRAWN);

        Button renew = new Button("Renew for 30 days", event ->
                perform(() -> service.renew(listing.id()), refreshed));
        renew.setVisible(editable);

        Paragraph explanation = new Paragraph(editable
                ? "Withdrawal hides this listing from search and your public profile. "
                + "Renewal makes it available for another 30 days. "
                + "Save any edits before withdrawing or renewing."
                : "Sold listings and listings removed by an administrator cannot be changed.");

        add(status, name, description, price, category, appointmentDuration, imageEditor, explanation, error);
        getFooter().add(new Button("Close", event -> close()),
                withdraw, renew, save);
    }

    private void perform(Runnable action, Runnable refreshed) {
        error.setText("");

        try {
            action.run();
        } catch (IllegalArgumentException | AccessDeniedException ex) {
            error.setText(ex.getMessage());
            return;
        } catch (DataAccessException | java.io.UncheckedIOException ex) {
            error.setText("The listing could not be updated. Please try again.");
            return;
        }

        close();
        refreshed.run();
    }
}

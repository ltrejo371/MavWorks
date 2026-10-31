package com.mavworks.dashboard;

import com.mavworks.service.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;
import java.io.ByteArrayInputStream;

public class EditAccountDialog extends Dialog {
    private byte[] selectedPicture;
    private final java.util.concurrent.atomic.AtomicInteger uploading = new java.util.concurrent.atomic.AtomicInteger();

    public EditAccountDialog(AccountDashboardService.DashboardData data, AccountEditingService service,
            LocalImageStorageService storage, Runnable saved) {
        setHeaderTitle("Edit account information");
        setWidth("32rem");
        TextField username = new TextField("Username");
        username.setValue(data.username());
        username.setMaxLength(30);
        username.setRequiredIndicatorVisible(true);
        username.setHelperText("3–30 letters, numbers, dots, underscores, or hyphens.");
        TextArea bio = new TextArea("Bio");
        bio.setValue(data.bio() == null ? "" : data.bio());
        bio.setMaxLength(2000);
        bio.setHelperText("Up to 2000 characters.");
        username.setWidthFull(); bio.setWidthFull();
        Paragraph error = new Paragraph();
        error.getElement().setAttribute("role", "alert");
        Paragraph selected = new Paragraph();
        Div preview = new Div();
        if (data.hasPicture()) preview.add(picture(data.userId()));
        Button save = new Button("Save");
        save.setDisableOnClick(true);
        Upload upload = new Upload((UploadHandler) event -> {
            uploading.incrementAndGet();
            try {
                event.getUI().accessSynchronously(() -> save.setEnabled(false));
                byte[] bytes = event.getInputStream().readNBytes(LocalImageStorageService.MAX_BYTES + 1);
                String extension = LocalImageStorageService.validate(bytes);
                event.getUI().accessSynchronously(() -> {
                    selectedPicture = bytes;
                    selected.setText("New profile picture selected.");
                    Image image = new Image("data:image/" + (extension.equals("jpg") ? "jpeg" : "png")
                            + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes), "Selected profile picture");
                    image.setWidth("96px"); image.setHeight("96px");
                    image.getStyle().set("object-fit", "cover");
                    preview.removeAll(); preview.add(image); error.setText("");
                });
            } catch (IllegalArgumentException | java.io.IOException ex) {
                event.reject("Select a valid JPEG or PNG image.");
                event.getUI().accessSynchronously(() -> error.setText(ex instanceof IllegalArgumentException
                        ? ex.getMessage() : "The picture could not be uploaded. Try again."));
            } finally { uploading.decrementAndGet(); }
        });
        upload.setMaxFiles(1);
        upload.setMaxFileSize(LocalImageStorageService.MAX_BYTES);
        upload.setAcceptedFileTypes("image/jpeg", "image/png", ".jpg", ".jpeg", ".png");
        upload.addFileRejectedListener(e -> error.setText(e.getErrorMessage()));
        upload.addAllFinishedListener(e -> { save.setEnabled(true); upload.clearFileList(); });
        save.addClickListener(e -> {
            if (uploading.get() > 0) { error.setText("Wait for the picture to finish uploading."); save.setEnabled(true); return; }
            String reference = null;
            boolean committed = false;
            try {
                if (selectedPicture != null) reference = storage.store(new ByteArrayInputStream(selectedPicture), "profile", null);
                service.save(username.getValue(), bio.getValue(), reference);
                committed = true;
                close(); saved.run();
            } catch (IllegalArgumentException | org.springframework.security.access.AccessDeniedException ex) {
                error.setText(ex.getMessage());
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                error.setText("This username is unavailable. Choose another username.");
            } catch (org.springframework.dao.DataAccessException | java.io.UncheckedIOException ex) {
                error.setText("Your information could not be saved. Your entries are still here; select Save to retry.");
            } finally {
                if (!committed && reference != null) {
                    try { storage.delete(reference); }
                    catch (RuntimeException ex) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Could not clean up unused profile picture", ex); }
                }
                save.setEnabled(true);
            }
        });
        add(username, bio, new Paragraph("Profile picture: JPEG or PNG, up to 5 MB and 20 megapixels."), preview, upload, selected, error);
        getFooter().add(new Button("Cancel", e -> close()), save);
        addOpenedChangeListener(e -> { if (!e.isOpened()) selectedPicture = null; });
    }

    public static Image picture(Long userId) {
        // A new URL forces an immediate reload after replacing a picture in the same view.
        Image image = new Image("profile-pictures/" + userId + "?v=" + java.util.UUID.randomUUID(), "Profile picture");
        image.setWidth("96px"); image.setHeight("96px");
        image.getStyle().set("object-fit", "cover").set("border-radius", "50%");
        return image;
    }
}

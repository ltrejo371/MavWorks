package com.mavworks.dashboard;

import com.mavworks.model.ListingCategory;
import com.mavworks.model.ListingStatus;
import com.mavworks.service.AccountDashboardService.OwnerListing;
import com.mavworks.service.ListingManagementService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;

public class ManageListingDialog extends Dialog {
    private final Paragraph error = new Paragraph();

    public ManageListingDialog(
            OwnerListing listing,
            ListingManagementService service,
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

        boolean editable = listing.status() != ListingStatus.SOLD
                && listing.status() != ListingStatus.REMOVED;

        name.setReadOnly(!editable);
        description.setReadOnly(!editable);
        price.setReadOnly(!editable);
        category.setReadOnly(!editable);

        error.getElement().setAttribute("role", "alert");

        Button save = new Button("Save changes", event ->
                perform(() -> service.updateDetails(
                        listing.id(),
                        name.getValue(),
                        description.getValue(),
                        price.getValue(),
                        category.getValue()), refreshed));
        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.setVisible(editable);

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

        add(status, name, description, price, category, explanation, error);
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
        } catch (DataAccessException ex) {
            error.setText("The listing could not be updated. Please try again.");
            return;
        }

        close();
        refreshed.run();
    }
}
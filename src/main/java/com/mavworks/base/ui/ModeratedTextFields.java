package com.mavworks.base.ui;

import com.mavworks.service.TextModerationService;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Field feedback for the same moderation rules enforced by the services. */
public final class ModeratedTextFields {
    private final List<Supplier<Boolean>> checks = new ArrayList<>();

    public void add(String label, TextField field) {
        field.addValueChangeListener(event -> field.setInvalid(false));
        add(label, field::getValue, field::setInvalid, field::setErrorMessage);
    }

    public void add(String label, TextArea field) {
        field.addValueChangeListener(event -> field.setInvalid(false));
        add(label, field::getValue, field::setInvalid, field::setErrorMessage);
    }

    private void add(String label, Supplier<String> value, Consumer<Boolean> invalid,
            Consumer<String> error) {
        checks.add(() -> {
            try {
                TextModerationService.validate(label, value.get());
                invalid.accept(false);
                return true;
            } catch (IllegalArgumentException ex) {
                error.accept(ex.getMessage());
                invalid.accept(true);
                return false;
            }
        });
    }

    public boolean validate() {
        boolean valid = true;
        for (var check : checks) valid &= check.get();
        return valid;
    }
}

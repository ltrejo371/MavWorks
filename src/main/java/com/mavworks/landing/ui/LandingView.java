package com.mavworks.landing.ui;

import com.mavworks.base.ui.ViewTitle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

@Route(value = "")
@PageTitle("Landing")
@Menu(order = 0, icon = "icons/clipboard-check.svg", title = "Welcome")
@PermitAll

class LandingView extends VerticalLayout {

    final TextField search;
    final Button createBtn;

    //Landing view. First view the user sees when they log in. Contains search bar.

    LandingView() {
        search = new TextField();
        search.setPlaceholder("Enter search");
        search.setAriaLabel("Search");
        search.setMinWidth("15em");

        createBtn = new Button("Go", event -> confirmSearch());
        createBtn.addThemeVariants(ButtonVariant.PRIMARY);

        var header = new HorizontalLayout();
        header.add(new ViewTitle("Welcome"));

        var toolbar = new VerticalLayout();
        toolbar.add(search, createBtn);
        toolbar.setWidthFull();
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        setSizeFull();
        add(header);
        add(toolbar);
    }

    private void confirmSearch() {
        //TODO: Implement search
    }
}

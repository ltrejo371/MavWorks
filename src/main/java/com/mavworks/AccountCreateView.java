package com.mavworks;

import com.mavworks.base.ui.ViewTitle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "create-account", autoLayout = false)
@PageTitle ("Create Account")
@AnonymousAllowed

public class AccountCreateView extends Main{
    final TextField username;
    final TextField password;
    final TextField passwordConfirm;
    final Button createBtn;

    public AccountCreateView(){
        username = new TextField();
        username.setPlaceholder("Username");
        username.setAriaLabel("Username");
        username.setMinWidth("15em");

        password = new TextField();
        password.setPlaceholder("Password");
        password.setAriaLabel("Password");
        password.setMinWidth("15em");

        passwordConfirm = new TextField();
        passwordConfirm.setPlaceholder("Confirm Password");
        passwordConfirm.setAriaLabel("Confirm Password");
        passwordConfirm.setMinWidth("15em");

        createBtn = new Button("Confirm", event -> confirmCreate());
        createBtn.addThemeVariants(ButtonVariant.PRIMARY);

        var layout = new VerticalLayout();
        layout.add(new ViewTitle("Create Account"), username, password, passwordConfirm, createBtn);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        add(layout);
    }

    public void confirmCreate(){
        if(password.getValue().equals(passwordConfirm.getValue())){
            if(!username.getValue().isEmpty() && !password.getValue().isEmpty()){
                if(true /*placeholder*/){
                    getUI().ifPresent(ui -> ui.navigate("login"));
                }
            }
        }
    }
}

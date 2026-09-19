package com.mavworks;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "login", autoLayout = false)
@PageTitle("Login")
@AnonymousAllowed 

public class LoginView extends Main implements BeforeEnterObserver{
    private final LoginForm login;
    private final Button createBtn;

    public LoginView(){
        login = new LoginForm();
        login.setAction("login");

        createBtn = new Button("New Account", event -> gotoCreateAccount());
        createBtn.addThemeVariants(ButtonVariant.PRIMARY);

        VerticalLayout layout = new VerticalLayout();
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        layout.add(login, createBtn);
        layout.setSizeFull();

        

        add(layout);
        setSizeFull();
    }

    public void gotoCreateAccount(){
        getUI().ifPresent(ui -> ui.navigate("create-account"));
    }

    @Override 
    public void beforeEnter(BeforeEnterEvent event){
        if(event.getLocation().getQueryParameters().getParameters().containsKey("error")){
            login.setError(true);
        }
    }
}

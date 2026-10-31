package com.mavworks.auth;

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

//Login view: The user must log in to access the application.

public class LoginView extends Main implements BeforeEnterObserver{
    private final LoginForm login;
    private final Button createBtn;
    private final com.vaadin.flow.component.html.Paragraph accountStatus = new com.vaadin.flow.component.html.Paragraph();

    public LoginView(){
        addClassName("auth-page");
        login = new LoginForm();
        login.setAction("login");

        createBtn = new Button("New Account", event -> gotoCreateAccount());
        createBtn.addThemeVariants(ButtonVariant.PRIMARY);

        VerticalLayout layout = new VerticalLayout();
        layout.addClassName("auth-card");
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        accountStatus.getElement().setAttribute("role", "alert");
        layout.add(new com.vaadin.flow.component.html.H1("MavWorks"),
                new com.vaadin.flow.component.html.Paragraph("Your campus marketplace. Sign in to get started."), accountStatus, login, createBtn);

        add(layout);
    }

    public void gotoCreateAccount(){
        getUI().ifPresent(ui -> ui.navigate("create-account"));
    }

    @Override 
    public void beforeEnter(BeforeEnterEvent event){
        accountStatus.setText(event.getLocation().getQueryParameters().getParameters().containsKey("banned")
                ? "This account has been banned from MavWorks." : "");
        if(event.getLocation().getQueryParameters().getParameters().containsKey("error")){
            login.setError(true);
        }
    }
}

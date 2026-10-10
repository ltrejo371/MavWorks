package com.mavworks.profile;

import com.mavworks.base.ui.ViewTitle;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route("profiles/:userId")
@PageTitle("Public Profile")
@PermitAll
public class ProfileView extends VerticalLayout {
    public ProfileView() {
        add(new ViewTitle("Public Profile"),
                new Paragraph("This screen is a placeholder. Its form and service integration are not implemented yet."));
    }
}

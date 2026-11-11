package com.mavworks.base.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;

/** Shared campus identity for application and public account pages. */
public final class UtaBrand extends Div {
    public UtaBrand() {
        addClassName("uta-brand");
        Image logo = new Image("images/uta-maverick.png", "UT Arlington Mavericks");
        logo.addClassName("uta-brand-logo");
        logo.setWidth("64px");
        logo.setHeight("50px");
        Span name = new Span("MavWorks");
        name.addClassName("app-name");
        Span campus = new Span("UT Arlington");
        campus.addClassName("uta-brand-campus");
        Div text = new Div(name, campus);
        text.addClassName("uta-brand-text");
        add(logo, text);
    }
}

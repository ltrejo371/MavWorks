package com.mavworks.auth;

import com.mavworks.base.ui.UtaBrand;
import com.mavworks.service.TermsOfServiceService;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Route(value = "terms", autoLayout = false)
@PageTitle("Terms of Service | MavWorks")
@AnonymousAllowed
public class TermsView extends Main {
    public TermsView(TermsOfServiceService termsService) {
        addClassName("auth-page");
        Article document = new Article();
        document.addClassNames("auth-card", "terms-document");
        document.add(new H1("Terms of Service"));
        termsService.getCurrentTerms().ifPresentOrElse(terms -> {
            String date = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(terms.getEffectiveAt());
            document.add(new Paragraph("Version " + terms.getVersion() + " · Effective " + date + " (UTC)"));
            Div content = new Div();
            content.addClassName("mw-prose");
            content.setText(terms.getContent());
            document.add(content);
        }, () -> document.add(new Paragraph("Terms are currently unavailable. Registration requires published terms.")));
        document.add(new RouterLink("Back to login", LoginView.class));
        add(new UtaBrand(), document);
    }
}

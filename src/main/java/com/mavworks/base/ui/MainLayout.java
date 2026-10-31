package com.mavworks.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;

import jakarta.annotation.security.PermitAll;

@Layout
@PermitAll

//Overarching layout: Handles the sidebar.

public final class MainLayout extends AppLayout implements com.vaadin.flow.router.AfterNavigationObserver {
    private final java.util.Map<String, com.vaadin.flow.component.html.Anchor> mobileLinks = new java.util.LinkedHashMap<>();

    MainLayout() {
        setPrimarySection(Section.DRAWER);
        addToDrawer(createApplicationHeader(), createApplicationDrawer(), createApplicationFooter());
        addClassName("mav-shell");
        var mobile = new com.vaadin.flow.component.html.Nav();
        mobile.addClassName("mobile-navigation");
        mobile.getElement().setAttribute("aria-label", "Main navigation");
        mobile.add(mobileLink("", "Home", com.vaadin.flow.component.icon.VaadinIcon.HOME),
                mobileLink("messages", "Messages", com.vaadin.flow.component.icon.VaadinIcon.COMMENTS),
                mobileLink("dashboard", "Dashboard", com.vaadin.flow.component.icon.VaadinIcon.USER));
        // Retain access to moderation on small screens for administrators only.
        if (MenuConfiguration.getMenuEntries().stream().anyMatch(e -> e.menuClass() == com.mavworks.admin.ReportsView.class)) {
            mobile.add(mobileLink("admin/reports", "Reports", com.vaadin.flow.component.icon.VaadinIcon.FLAG));
        }
        addToNavbar(mobile);
    }

    private com.vaadin.flow.component.html.Anchor mobileLink(String path, String label, com.vaadin.flow.component.icon.VaadinIcon symbol) {
        var link = new com.vaadin.flow.component.html.Anchor(path, "");
        var icon = symbol.create(); icon.getElement().setAttribute("aria-hidden", "true");
        link.add(icon, new Span(label));
        link.getElement().setAttribute("aria-label", label);
        mobileLinks.put(path, link); return link;
    }

    @Override public void afterNavigation(com.vaadin.flow.router.AfterNavigationEvent event) {
        String path = event.getLocation().getPath();
        String selected = path.startsWith("messages") ? "messages" : path.equals("dashboard") ? "dashboard"
                : path.startsWith("admin/reports") ? "admin/reports" : "";
        mobileLinks.forEach((route, link) -> {
            if (route.equals(selected)) link.getElement().setAttribute("aria-current", "page");
            else link.getElement().removeAttribute("aria-current");
        });
    }

    private Component createApplicationHeader() {
        var appLogo = new Avatar("MavWorks");
        appLogo.addClassName("app-logo");
        appLogo.addThemeVariants(AvatarVariant.AURA_FILLED, AvatarVariant.XSMALL);

        var appName = new Span("MavWorks");
        appName.addClassName("app-name");

        var header = new HorizontalLayout(appLogo, appName);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setPadding(true);
        return header;
    }

    private Component createApplicationDrawer() {
        var scroller = new Scroller(createSideNav());
        scroller.addThemeVariants(ScrollerVariant.OVERFLOW_INDICATORS);
        return scroller;
    }

    private Component createApplicationFooter() {
        var footer = new VerticalLayout(new Span("MavWorks · Campus marketplace"));
        footer.setAlignItems(FlexComponent.Alignment.CENTER);
        footer.addClassName("app-footer");
        return footer;
    }

    private SideNav createSideNav() {
        var nav = new SideNav();
        nav.setMinWidth(200, Unit.PIXELS);
        MenuConfiguration.getMenuEntries().forEach(entry -> nav.addItem(createSideNavItem(entry)));
        return nav;
    }

    private SideNavItem createSideNavItem(MenuEntry menuEntry) {
        if (menuEntry.icon() != null) {
            Component icon = null;
            if (menuEntry.icon().contains(".svg")) {
                icon = new SvgIcon(menuEntry.icon());
            } else {
                icon = new Icon(menuEntry.icon());
            }
            return new SideNavItem(menuEntry.title(), menuEntry.menuClass(), icon);
        } else {
            return new SideNavItem(menuEntry.title(), menuEntry.menuClass());
        }
    }
}

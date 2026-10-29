package com.mavworks.model;

import java.util.List;

public class Admin extends User implements ModerationActions {
    @Override
    public List<Report> viewReports() {
        // TODO: Implement.
        return List.of();
    }

    @Override
    public void validateReport() {
        // TODO: Implement.
    }

    @Override
    public void banUser() {
        // TODO: Implement.
    }

    @Override
    public void removeListing() {
        // TODO: Implement.
    }
}

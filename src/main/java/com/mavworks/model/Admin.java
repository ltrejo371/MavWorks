package com.mavworks.model;

import java.util.List;

public class Admin extends User implements ModerationActions {
    @Override
    public List<Report> viewReports() {
        // TODO: Implement.
        return List.of();
    }

    @Override
    public void validateReport(Report report, ReportStatus decision, ModerationAction action, String explanation) {
        if (report == null) throw new IllegalArgumentException("Select a report.");
        report.resolve(this, decision, action, explanation);
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

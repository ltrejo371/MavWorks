package com.mavworks.model;

import java.util.List;

/** Administrative moderation actions defined by the UML diagram. */
public interface ModerationActions {
    List<Report> viewReports();

    void validateReport(Report report, ReportStatus decision, ModerationAction action, String explanation);

    void banUser();

    void removeListing();
}

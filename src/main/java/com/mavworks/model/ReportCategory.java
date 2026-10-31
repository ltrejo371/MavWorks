package com.mavworks.model;

public enum ReportCategory {
    SCAM_OR_FRAUD("Scam or fraud"),
    HARASSMENT("Harassment or threats"),
    SPAM("Spam"),
    INAPPROPRIATE_CONTENT("Inappropriate content"),
    PROHIBITED_ITEMS("Prohibited products or services"),
    IMPERSONATION("Impersonation"),
    OTHER("Other terms-of-service violation");

    private final String label;
    ReportCategory(String label) { this.label = label; }
    public String getLabel() { return label; }
}

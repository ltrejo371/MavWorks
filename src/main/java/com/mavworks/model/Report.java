package com.mavworks.model;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "reports", indexes = @Index(name = "idx_reports_pending", columnList = "status, created_at, id"))
public class Report {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Preserve reports for administrators even if either account is deleted later.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", updatable = false)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User reporter;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_user_id", updatable = false)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User reportedUser;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false)
    private ReportCategory category;
    @Column(length = 4000, updatable = false)
    private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ReportStatus status = ReportStatus.PENDING;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User reviewedBy;
    private Instant reviewedAt;
    @Enumerated(EnumType.STRING)
    private ModerationAction action;
    @Column(length = 2000)
    private String decisionNote;

    /** Authorization and locking are enforced by ReportService. */
    public void resolve(User admin, ReportStatus decision, ModerationAction action, String note) {
        if (status != ReportStatus.PENDING) throw new IllegalStateException("This report has already been reviewed. Reload to see the saved decision.");
        if (admin == null || admin.getId() == null) throw new IllegalArgumentException("A reviewing administrator is required.");
        if (decision == null || decision == ReportStatus.PENDING) throw new IllegalArgumentException("Choose VALID or INVALID, or leave without saving.");
        String text = note == null ? "" : note.trim();
        if (text.length() > 2000) throw new IllegalArgumentException("The explanation must be at most 2000 characters.");
        if (decision == ReportStatus.VALID && (action == null || text.isBlank())) {
            throw new IllegalArgumentException("Choose a warning or ban and provide an explanation for the reported user.");
        }
        if (decision == ReportStatus.INVALID && action != null) throw new IllegalArgumentException("An invalid report cannot issue a warning or ban.");
        if (decision == ReportStatus.VALID && reportedUser == null) throw new IllegalArgumentException("The reported account was deleted and cannot receive a warning or ban.");
        status = decision;
        this.action = action;
        decisionNote = text;
        reviewedBy = admin;
        reviewedAt = Instant.now();
        if (action == ModerationAction.BAN) reportedUser.ban();
    }

    public User getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public ModerationAction getAction() { return action; }
    public String getDecisionNote() { return decisionNote; }

    protected Report() {}

    Report(User reporter, User reportedUser, ReportCategory category, String reason) {
        if (reporter == null || reporter.getId() == null || reportedUser == null || reportedUser.getId() == null
                || reporter.getId().equals(reportedUser.getId())) {
            throw new IllegalArgumentException("Select another account to report.");
        }
        if (category == null) throw new IllegalArgumentException("Select a report category.");
        String text = reason == null ? "" : reason.trim();
        if (text.length() > 4000) throw new IllegalArgumentException("The reason must be at most 4000 characters.");
        this.reporter = reporter;
        this.reportedUser = reportedUser;
        this.category = category;
        this.reason = text;
    }
    public Long getId() { return id; }
    public User getReporter() { return reporter; }
    public User getReportedUser() { return reportedUser; }
    public ReportCategory getCategory() { return category; }
    public String getReason() { return reason; }
    public ReportStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}

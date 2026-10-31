package com.mavworks.service;

import com.mavworks.model.*;
import com.mavworks.repository.*;
import com.mavworks.security.CurrentUser;
import java.time.Instant;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    public record ReportSummary(Long id, String reporter, String reportedUser, ReportCategory category,
            String reason, ReportStatus status, Instant createdAt) {}
    public record ReportDetails(ReportSummary report, long priorValidReports, boolean accountDeleted,
            boolean accountBanned, String reviewedBy, Instant reviewedAt, ModerationAction action, String explanation) {}
    private final CurrentUser current;
    private final UserRepository users;
    private final ReportRepository reports;

    public ReportService(CurrentUser current, UserRepository users, ReportRepository reports) {
        this.current = current;
        this.users = users;
        this.reports = reports;
    }

    @Transactional
    public Long reportUser(Long targetId, ReportCategory category, String reason) {
        var reporter = current.username().flatMap(users::findByUsername)
                .orElseThrow(() -> new AccessDeniedException("Sign in to report an account."));
        if (targetId == null) throw new IllegalArgumentException("Select an account to report.");
        var target = users.findById(targetId).orElseThrow(() -> new IllegalArgumentException("Account not available."));
        // A block does not prevent reporting misconduct. Reporting does not create a block.
        return reports.saveAndFlush(reporter.reportUser(target, category, reason)).getId();
    }

    @Transactional(readOnly = true)
    public Page<ReportSummary> pendingReports(int page) {
        return reports(ReportStatus.PENDING, page);
    }

    private User administrator() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var user = current.username().flatMap(users::findByUsername);
        if (user.isEmpty() || authentication == null
                || authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new AccessDeniedException("Only administrators can review reports.");
        }
        return user.get();
    }

    @Transactional(readOnly = true)
    public Page<ReportSummary> reports(ReportStatus status, int page) {
        administrator();
        if (status == null) throw new IllegalArgumentException("Select a report status.");
        return reports.findByStatusOrderByCreatedAtAscIdAsc(status, PageRequest.of(page, 50)).map(this::summary);
    }

    private ReportSummary summary(Report r) {
        return new ReportSummary(r.getId(), r.getReporter() == null ? "Deleted account" : r.getReporter().getUsername(),
                        r.getReportedUser() == null ? "Deleted account" : r.getReportedUser().getUsername(),
                        r.getCategory(), r.getReason(), r.getStatus(), r.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public ReportDetails details(Long id) {
        administrator();
        if (id == null) throw new IllegalArgumentException("Select a report.");
        var r = reports.findById(id).orElseThrow(() -> new IllegalArgumentException("Report not found."));
        var target = r.getReportedUser();
        long count = target == null ? 0 : reports.countByReportedUserIdAndStatus(target.getId(), ReportStatus.VALID);
        if (r.getStatus() == ReportStatus.VALID && count > 0) count--;
        return new ReportDetails(summary(r), count, target == null, target != null && target.isBanned(),
                r.getReviewedBy() == null ? null : r.getReviewedBy().getUsername(), r.getReviewedAt(), r.getAction(), r.getDecisionNote());
    }

    @Transactional
    public void validateReport(Long id, ReportStatus decision, ModerationAction action, String explanation) {
        var admin = administrator();
        if (id == null) throw new IllegalArgumentException("Select a report.");
        var report = reports.findForDecision(id).orElseThrow(() -> new IllegalArgumentException("Report not found."));
        if (report.getReportedUser() != null && report.getReportedUser().getId().equals(admin.getId())) {
            throw new AccessDeniedException("Another administrator must review a report against your own account.");
        }
        report.resolve(admin, decision, action, explanation);
        reports.flush();
    }
}

package com.mavworks.service;

import com.mavworks.model.*;
import com.mavworks.repository.*;
import com.mavworks.security.CurrentUser;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@Service
public class ModerationNoticeService {
    public record Notice(Long reportId, String offense, String explanation, int warningNumber, boolean banned)
            implements java.io.Serializable {}
    private final ReportRepository reports;
    private final UserRepository users;
    private final CurrentUser current;

    public ModerationNoticeService(ReportRepository reports, UserRepository users, CurrentUser current) {
        this.reports = reports; this.users = users; this.current = current;
    }

    // Called only with an account ID established by password verification or the security context.
    @Transactional(readOnly = true)
    public List<Notice> forAccount(Long userId, boolean pendingOnly) {
        var history = reports.findByReportedUserIdAndStatusOrderByReviewedAtAscIdAsc(userId, ReportStatus.VALID);
        List<Notice> notices = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            var r = history.get(i);
            if (!pendingOnly || r.getNoticeAcknowledgedAt() == null) {
                notices.add(new Notice(r.getId(), r.getCategory().getLabel(), r.getDecisionNote(), i + 1,
                        r.getAction() == ModerationAction.BAN));
            }
        }
        return List.copyOf(notices);
    }

    private User account() {
        return current.username().flatMap(users::findByUsername)
                .orElseThrow(() -> new AccessDeniedException("Sign in to view account notices."));
    }

    @Transactional(readOnly = true)
    public List<Notice> pending() { return forAccount(account().getId(), true); }

    @Transactional
    public void acknowledge(List<Long> ids) {
        var account = account();
        for (var report : reports.findAllById(ids)) {
            if (report.getReportedUser() == null || !report.getReportedUser().getId().equals(account.getId()))
                throw new AccessDeniedException("This notice belongs to another account.");
            if (report.getStatus() == ReportStatus.VALID) report.acknowledgeNotice();
        }
    }
}

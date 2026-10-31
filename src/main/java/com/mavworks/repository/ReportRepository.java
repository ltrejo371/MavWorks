package com.mavworks.repository;

import com.mavworks.model.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface ReportRepository extends JpaRepository<Report, Long> {
    @EntityGraph(attributePaths = "reportedUser")
    java.util.List<Report> findByReporterIdOrderByCreatedAtDescIdDesc(Long reporterId);
    @EntityGraph(attributePaths = {"reporter", "reportedUser"})
    Page<Report> findByStatusOrderByCreatedAtAscIdAsc(ReportStatus status, Pageable page);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Report r where r.id = :id")
    java.util.Optional<Report> findForDecision(@org.springframework.data.repository.query.Param("id") Long id);

    long countByReportedUserIdAndStatus(Long userId, ReportStatus status);

    @Query("""
            select new com.mavworks.dto.AccountWarning(r.category, r.decisionNote, r.reviewedAt)
            from Report r where r.reportedUser.id = :userId and r.status = com.mavworks.model.ReportStatus.VALID
              and r.action = com.mavworks.model.ModerationAction.WARNING order by r.reviewedAt desc, r.id desc
            """)
    java.util.List<com.mavworks.dto.AccountWarning> findWarnings(@org.springframework.data.repository.query.Param("userId") Long userId);
}

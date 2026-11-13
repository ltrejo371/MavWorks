package com.mavworks.repository;

import com.mavworks.model.EmailVerification;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from EmailVerification v where v.email = :email")
    Optional<EmailVerification> findForEmail(@Param("email") String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from EmailVerification v where v.token = :token")
    Optional<EmailVerification> findForToken(@Param("token") UUID token);
}

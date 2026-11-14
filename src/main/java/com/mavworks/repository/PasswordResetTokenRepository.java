package com.mavworks.repository;

import com.mavworks.model.PasswordResetToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordResetToken token where token.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findForToken(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("delete from PasswordResetToken token where token.user.id = :userId")
    void deleteForUser(@Param("userId") Long userId);
}

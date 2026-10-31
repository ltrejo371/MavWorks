package com.mavworks.repository;

import com.mavworks.model.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {
    @org.springframework.data.jpa.repository.Query("select b.blocked.username from UserBlock b where b.blocker.id = :userId order by b.blocked.username")
    java.util.List<String> findBlockedUsernames(@org.springframework.data.repository.query.Param("userId") Long userId);
    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    @org.springframework.data.jpa.repository.Query("""
            select case when b.blocker.id = :userId then b.blocked.id else b.blocker.id end
            from UserBlock b where b.blocker.id = :userId or b.blocked.id = :userId
            """)
    java.util.Set<Long> findHiddenUserIds(@org.springframework.data.repository.query.Param("userId") Long userId);
}

package com.mavworks.repository;

import com.mavworks.model.TermsOfService;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.time.Instant;

public interface TermsOfServiceRepository extends JpaRepository<TermsOfService, Long> {
    Optional<TermsOfService> findFirstByEffectiveAtLessThanEqualOrderByEffectiveAtDesc(Instant now);
}

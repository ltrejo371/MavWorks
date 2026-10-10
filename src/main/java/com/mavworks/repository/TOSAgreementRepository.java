package com.mavworks.repository;

import com.mavworks.model.TOSAgreement;
import org.springframework.data.jpa.repository.JpaRepository;


public interface TOSAgreementRepository extends JpaRepository<TOSAgreement, Long> {
    boolean existsByUserIdAndTermsId(Long userId, Long termsId);
}

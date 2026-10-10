package com.mavworks.service;

import com.mavworks.model.TermsOfService;
import com.mavworks.repository.TermsOfServiceRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TermsOfServiceServiceImpl implements TermsOfServiceService {
    private final TermsOfServiceRepository terms;

    public TermsOfServiceServiceImpl(TermsOfServiceRepository terms) {
        this.terms = terms;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TermsOfService> getCurrentTerms() {
        return terms.findFirstByEffectiveAtLessThanEqualOrderByEffectiveAtDesc(Instant.now());
    }
}

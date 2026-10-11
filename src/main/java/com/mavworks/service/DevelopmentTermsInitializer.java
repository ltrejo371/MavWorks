package com.mavworks.service;

import com.mavworks.model.TermsOfService;
import com.mavworks.repository.TermsOfServiceRepository;
import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Development content only. Replace with team-approved terms before deployment. */
@Component
@ConditionalOnProperty(name = "mavworks.seed-development-terms", havingValue = "true")
public class DevelopmentTermsInitializer implements ApplicationRunner {
    private final TermsOfServiceRepository terms;

    public DevelopmentTermsInitializer(TermsOfServiceRepository terms) {
        this.terms = terms;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (terms.count() == 0) {
            TermsOfService draft = new TermsOfService();
            draft.setVersion("development-v1");
            draft.setEffectiveAt(Instant.now());
            draft.setContent("Development demo terms — not final terms of service. "
                    + "This MavWorks build is a class-project prototype. Use test data only. "
                    + "By checking the box you acknowledge this is a development demonstration.");
            terms.save(draft);
        }
    }
}

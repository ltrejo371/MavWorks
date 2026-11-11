package com.mavworks.service;

import com.mavworks.model.TermsOfService;
import com.mavworks.repository.TermsOfServiceRepository;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds bundled terms without changing documents that users already accepted. */
@Component
@ConditionalOnProperty(name = "mavworks.seed-development-terms", havingValue = "true")
public class DevelopmentTermsInitializer implements ApplicationRunner {
    private final TermsOfServiceRepository terms;

    public DevelopmentTermsInitializer(TermsOfServiceRepository terms) {
        this.terms = terms;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        // Only upgrade the original demo. Preserve custom terms and future publications.
        var existing = terms.findAll();
        if (existing.isEmpty() || existing.stream().allMatch(t -> "development-v1".equals(t.getVersion()))) {
            TermsOfService draft = new TermsOfService();
            draft.setVersion("2026-11-09-v1");
            draft.setEffectiveAt(Instant.now());
            draft.setContent(new ClassPathResource("legal/terms-of-service.txt")
                    .getContentAsString(StandardCharsets.UTF_8));
            terms.save(draft);
        }
    }
}

package com.mavworks;

import com.mavworks.model.ReportCategory;
import com.mavworks.model.TermsOfService;
import com.mavworks.repository.TermsOfServiceRepository;
import com.mavworks.service.DevelopmentTermsInitializer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TermsPublicationTest {
    @Test void publishesFullTermsForAnEmptyDatabase() throws Exception {
        var repository = mock(TermsOfServiceRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        new DevelopmentTermsInitializer(repository).run(null);
        var saved = ArgumentCaptor.forClass(TermsOfService.class);
        verify(repository).save(saved.capture());
        var terms = saved.getValue();
        assertEquals("2026-11-09-v1", terms.getVersion());
        assertNotNull(terms.getEffectiveAt());
        for (var category : ReportCategory.values()) {
            assertTrue(terms.getContent().contains(category.getLabel() + ":"), category.name());
        }
    }

    @Test void upgradesDemoWithoutRewritingPreviouslyAcceptedText() throws Exception {
        var repository = mock(TermsOfServiceRepository.class);
        var demo = terms("development-v1");
        demo.setContent("Previously accepted demo text");
        when(repository.findAll()).thenReturn(List.of(demo));
        new DevelopmentTermsInitializer(repository).run(null);
        var saved = ArgumentCaptor.forClass(TermsOfService.class);
        verify(repository).save(saved.capture());
        assertNotSame(demo, saved.getValue());
        assertEquals("development-v1", demo.getVersion());
        assertEquals("Previously accepted demo text", demo.getContent());
    }

    @Test void preservesCustomAndAlreadyPublishedTerms() throws Exception {
        for (String version : List.of("custom-v1", "2026-11-09-v1", "future-v2")) {
            var repository = mock(TermsOfServiceRepository.class);
            when(repository.findAll()).thenReturn(List.of(terms("development-v1"), terms(version)));
            new DevelopmentTermsInitializer(repository).run(null);
            verify(repository, never()).save(any());
        }
    }

    private TermsOfService terms(String version) {
        var terms = new TermsOfService();
        terms.setVersion(version);
        return terms;
    }
}

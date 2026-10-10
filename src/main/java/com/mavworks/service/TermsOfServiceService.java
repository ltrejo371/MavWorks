package com.mavworks.service;

import com.mavworks.model.TermsOfService;
import java.util.Optional;

/** Implementation contract only; not yet wired into the views. */
public interface TermsOfServiceService {
    Optional<TermsOfService> getCurrentTerms();
}

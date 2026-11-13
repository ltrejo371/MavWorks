package com.mavworks.service;

import java.io.IOException;
import java.io.InputStream;

/** Image storage shared by listing and profile workflows. */
public interface ImageStorageService {

    String store(InputStream content, String originalFilename, String contentType);

    InputStream open(String reference) throws IOException;

    void validateOwnedReference(String reference);

    void delete(String reference);
}
package com.mavworks.service;

import java.io.InputStream;

/** Validated image storage used by the listing creation form. */
public interface ImageStorageService {
    // Validate type, size and content; create a server-controlled storage reference.
    String store(InputStream content, String originalFilename, String contentType);
    void delete(String storageReference);
}

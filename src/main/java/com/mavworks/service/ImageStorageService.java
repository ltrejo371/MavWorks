package com.mavworks.service;

import java.io.InputStream;

/** Implementation contract only; not yet wired into the views. */
public interface ImageStorageService {
    // Validate type, size and content; create a server-controlled storage reference.
    String store(InputStream content, String originalFilename, String contentType);
    void delete(String storageReference);
}

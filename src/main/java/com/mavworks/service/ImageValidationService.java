package com.mavworks.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

public final class ImageValidationService {
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private ImageValidationService() {
    }

    public static String validate(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException(
                    "Each image must be nonempty and at most 5 MB.");
        }

        try (var input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(bytes))) {

            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException(
                        "Select a supported JPEG or PNG image.");
            }

            var reader = readers.next();
            try {
                String format = reader.getFormatName()
                        .toLowerCase(java.util.Locale.ROOT);

                if (!format.equals("png") && !format.equals("jpeg")) {
                    throw new IllegalArgumentException(
                            "Select a supported JPEG or PNG image.");
                }

                reader.setInput(input);

                if ((long) reader.getWidth(0) * reader.getHeight(0)
                        > 20_000_000) {
                    throw new IllegalArgumentException(
                            "Images must be at most 20 megapixels.");
                }

                reader.read(0);
                return format.equals("jpeg") ? "jpg" : "png";
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException(
                    "The image cannot be read. Select another JPEG or PNG.", ex);
        }
    }
}
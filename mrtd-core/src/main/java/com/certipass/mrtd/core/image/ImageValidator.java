package com.certipass.mrtd.core.image;

import com.certipass.mrtd.core.model.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Validates uploaded image bytes before OCR processing.
 */
public final class ImageValidator {
    private static final long DEFAULT_MAX_SIZE_BYTES = 10 * 1024 * 1024;
    private final long maxSizeBytes;

    public ImageValidator() {
        this(DEFAULT_MAX_SIZE_BYTES);
    }

    public ImageValidator(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public ImageValidationResult validate(byte[] imageData) {
        if (imageData == null || imageData.length == 0) {
            return invalid("Image data is empty");
        }
        if (imageData.length > maxSizeBytes) {
            return invalid("Image exceeds maximum size of " + maxSizeBytes + " bytes");
        }

        String format = detectFormat(imageData);
        if (format == null) {
            return invalid("Only JPEG and PNG images are supported");
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image == null) {
                return invalid("Image bytes could not be decoded");
            }
            return new ImageValidationResult.Valid(
                    new ImageMetadata(image.getWidth(), image.getHeight(), format, imageData.length));
        } catch (IOException exception) {
            return invalid("Image bytes could not be decoded");
        }
    }

    private ImageValidationResult.Invalid invalid(String message) {
        return new ImageValidationResult.Invalid(ErrorCode.INVALID_IMAGE, message);
    }

    private String detectFormat(byte[] imageData) {
        if (imageData.length >= 3
                && unsigned(imageData[0]) == 0xFF
                && unsigned(imageData[1]) == 0xD8
                && unsigned(imageData[2]) == 0xFF) {
            return "JPEG";
        }
        if (imageData.length >= 8
                && unsigned(imageData[0]) == 0x89
                && imageData[1] == 0x50
                && imageData[2] == 0x4E
                && imageData[3] == 0x47) {
            return "PNG";
        }
        return null;
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }
}

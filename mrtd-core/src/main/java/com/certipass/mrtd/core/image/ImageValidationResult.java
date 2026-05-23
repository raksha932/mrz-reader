package com.certipass.mrtd.core.image;

import com.certipass.mrtd.core.model.ErrorCode;

/**
 * Result of validating image bytes before OCR.
 */
public sealed interface ImageValidationResult
        permits ImageValidationResult.Valid, ImageValidationResult.Invalid {

    record Valid(ImageMetadata metadata) implements ImageValidationResult {
    }

    record Invalid(ErrorCode errorCode, String message) implements ImageValidationResult {
    }
}

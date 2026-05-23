package com.certipass.mrtd.core.model;

/**
 * Normalized OCR confidence score in the inclusive range 0.0 to 1.0.
 */
public record ConfidenceScore(double value) {
    public ConfidenceScore {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Confidence score must be between 0.0 and 1.0");
        }
    }
}

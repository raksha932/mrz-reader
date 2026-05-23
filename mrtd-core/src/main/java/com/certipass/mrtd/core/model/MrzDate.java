package com.certipass.mrtd.core.model;

import java.time.LocalDate;
import java.util.Optional;

/**
 * MRZ date value preserving the raw YYMMDD text even when parsing fails.
 */
public record MrzDate(String rawValue, LocalDate parsedValue, boolean valid) {
    public MrzDate {
        if (rawValue == null) {
            throw new IllegalArgumentException("Raw MRZ date must be present");
        }
        if (valid && (rawValue.isBlank() || parsedValue == null)) {
            throw new IllegalArgumentException("A valid MRZ date must include a parsed value");
        }
    }

    public Optional<LocalDate> parsed() {
        return Optional.ofNullable(parsedValue);
    }
}

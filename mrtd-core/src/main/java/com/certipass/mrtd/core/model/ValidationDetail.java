package com.certipass.mrtd.core.model;

/**
 * One validation outcome produced by checksum or field-logic checks.
 */
public record ValidationDetail(
        String checkName,
        String fieldName,
        boolean pass,
        ValidationSeverity severity,
        String message,
        Integer expectedDigit,
        Integer actualDigit
) {
}

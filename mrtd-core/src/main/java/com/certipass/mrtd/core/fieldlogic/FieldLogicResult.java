package com.certipass.mrtd.core.fieldlogic;

import com.certipass.mrtd.core.model.ValidationSeverity;

/**
 * Outcome of one non-checksum field logic check.
 */
public record FieldLogicResult(
        String checkName,
        String fieldName,
        boolean pass,
        ValidationSeverity severity,
        String message
) {
}

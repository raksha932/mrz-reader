package com.certipass.mrtd.core.model;

import java.util.List;

/**
 * Completed MRZ read with parsed identity data and validation details.
 */
public record MrzSuccessResult(
        Verdict verdict,
        IdentityData identityData,
        List<ValidationDetail> validations,
        ConfidenceScore confidenceScore,
        MrzRawData rawMrz,
        ResultMetadata metadata
) implements MrzReadResult {
    public MrzSuccessResult {
        validations = List.copyOf(validations);
    }
}

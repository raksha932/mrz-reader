package com.certipass.mrtd.core.detection;

import com.certipass.mrtd.core.model.ErrorCode;

/**
 * Result of MRZ format detection.
 */
public sealed interface MrzFormatDetectionResult
        permits MrzFormatDetectionResult.Detected, MrzFormatDetectionResult.Failure {

    record Detected(MrzFormat format) implements MrzFormatDetectionResult {
    }

    record Failure(ErrorCode errorCode, String message) implements MrzFormatDetectionResult {
    }
}

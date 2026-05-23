package com.certipass.mrtd.core.model;

import com.certipass.mrtd.core.detection.MrzFormat;
import java.time.Instant;

/**
 * Runtime metadata for an MRZ read operation.
 */
public record ResultMetadata(
        long processingTimeMs,
        double ocrConfidence,
        MrzFormat mrzFormat,
        String apiVersion,
        Instant timestamp
) {
}

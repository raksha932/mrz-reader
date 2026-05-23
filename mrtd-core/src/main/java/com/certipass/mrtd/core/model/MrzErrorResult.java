package com.certipass.mrtd.core.model;

import java.time.Instant;
import java.util.List;

/**
 * Terminal MRZ read failure. Business failures are modeled as values.
 */
public record MrzErrorResult(
        ErrorCode errorCode,
        String message,
        Instant timestamp,
        List<String> ocrLines,
        List<String> originalOcrLines
) implements MrzReadResult {
    public MrzErrorResult(ErrorCode errorCode, String message, Instant timestamp) {
        this(errorCode, message, timestamp, List.of(), List.of());
    }

    public MrzErrorResult(ErrorCode errorCode, String message, Instant timestamp, List<String> ocrLines) {
        this(errorCode, message, timestamp, ocrLines, ocrLines);
    }

    public MrzErrorResult {
        ocrLines = ocrLines == null ? List.of() : List.copyOf(ocrLines);
        originalOcrLines = originalOcrLines == null || originalOcrLines.isEmpty()
                ? ocrLines
                : List.copyOf(originalOcrLines);
    }

    public boolean corrected() {
        return !ocrLines.equals(originalOcrLines);
    }
}

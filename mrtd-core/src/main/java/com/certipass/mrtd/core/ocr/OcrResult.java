package com.certipass.mrtd.core.ocr;

import java.util.List;

/**
 * Successful OCR extraction payload returned by an OCR implementation.
 */
public record OcrResult(
        List<String> mrzLines,
        double confidence,
        double qualityScore,
        OcrBoundingBox zoneBoundingBox,
        List<String> preprocessingApplied
) {
    public OcrResult {
        mrzLines = List.copyOf(mrzLines);
        preprocessingApplied = List.copyOf(preprocessingApplied);
    }
}

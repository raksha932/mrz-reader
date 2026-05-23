package com.certipass.mrtd.core.ocr;

import com.certipass.mrtd.core.model.ErrorCode;

/**
 * Sealed boundary result for OCR calls. OCR failures remain values.
 */
public sealed interface OcrExtractionResult
        permits OcrExtractionResult.Success, OcrExtractionResult.Failure {

    record Success(OcrResult result) implements OcrExtractionResult {
    }

    record Failure(ErrorCode errorCode, String message) implements OcrExtractionResult {
    }
}

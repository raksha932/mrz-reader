package com.certipass.mrtd.core.ocr;

/**
 * Boundary between the pure Java core and any OCR implementation.
 */
public interface OcrClient {
    OcrExtractionResult extractMrz(byte[] imageData);
}

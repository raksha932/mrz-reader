package com.certipass.mrtd.core.ocr;

/**
 * Bounding box of the detected MRZ zone in source image coordinates.
 */
public record OcrBoundingBox(int x, int y, int width, int height) {
}

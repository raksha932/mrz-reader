package com.certipass.mrtd.core.image;

/**
 * Basic image characteristics extracted before OCR.
 */
public record ImageMetadata(int width, int height, String format, long sizeBytes) {
}

package com.certipass.mrtd.core.model;

/**
 * Domain and delivery error codes shared by the MRZ product surface.
 */
public enum ErrorCode {
    INVALID_IMAGE,
    NO_DOCUMENT_FOUND,
    IMAGE_QUALITY_LOW,
    MRZ_NOT_DETECTED,
    MRZ_INCOMPLETE,
    UNSUPPORTED_FORMAT,
    RATE_LIMIT_EXCEEDED,
    UNAUTHORIZED,
    CHECK_DIGIT_FAILURE,
    DOCUMENT_EXPIRED,
    OCR_SERVICE_UNAVAILABLE
}

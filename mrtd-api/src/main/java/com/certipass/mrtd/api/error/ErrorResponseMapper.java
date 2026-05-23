package com.certipass.mrtd.api.error;

import com.certipass.mrtd.core.model.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

@Component
public class ErrorResponseMapper {
    public HttpStatusCode statusFor(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_IMAGE -> HttpStatus.BAD_REQUEST;
            case OCR_SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case NO_DOCUMENT_FOUND, IMAGE_QUALITY_LOW, MRZ_NOT_DETECTED, MRZ_INCOMPLETE, UNSUPPORTED_FORMAT ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case RATE_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case CHECK_DIGIT_FAILURE, DOCUMENT_EXPIRED -> HttpStatus.OK;
        };
    }
}

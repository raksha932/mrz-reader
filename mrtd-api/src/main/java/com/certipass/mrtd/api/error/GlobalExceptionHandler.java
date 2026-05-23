package com.certipass.mrtd.api.error;

import com.certipass.mrtd.core.model.ErrorCode;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private final com.certipass.mrtd.api.web.RequestIdProvider requestIdProvider;

    public GlobalExceptionHandler(com.certipass.mrtd.api.web.RequestIdProvider requestIdProvider) {
        this.requestIdProvider = requestIdProvider;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
        return ResponseEntity
                .status(exception.httpStatus())
                .body(ApiErrorResponse.of(
                        exception.errorCode(),
                        exception.getMessage(),
                        requestIdProvider.currentRequestId()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        return invalidImage("Uploaded image exceeds the maximum allowed size.");
    }

    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
    ResponseEntity<ApiErrorResponse> handleMultipartFailure(Exception exception) {
        return invalidImage("A multipart form-data request with a file part named 'file' is required.");
    }

    @ExceptionHandler(IOException.class)
    ResponseEntity<ApiErrorResponse> handleIoException(IOException exception) {
        return invalidImage("Uploaded image could not be read.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(
                        ErrorCode.OCR_SERVICE_UNAVAILABLE,
                        "Unexpected server error while processing the MRZ request.",
                        requestIdProvider.currentRequestId()));
    }

    private ResponseEntity<ApiErrorResponse> invalidImage(String message) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ErrorCode.INVALID_IMAGE, message, requestIdProvider.currentRequestId()));
    }
}

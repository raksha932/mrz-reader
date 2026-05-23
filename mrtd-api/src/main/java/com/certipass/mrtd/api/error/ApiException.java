package com.certipass.mrtd.api.error;

import com.certipass.mrtd.core.model.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class ApiException extends RuntimeException {
    private final ErrorCode errorCode;
    private final HttpStatusCode httpStatus;

    public ApiException(ErrorCode errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public HttpStatusCode httpStatus() {
        return httpStatus;
    }
}

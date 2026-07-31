package com.okaynow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base unchecked exception for API-facing errors. Subclasses fix the HTTP status.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

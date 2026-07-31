package com.okaynow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 409 Conflict: the request is valid but conflicts with current resource state
 * (e.g. shift already claimed, overlapping booking).
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}

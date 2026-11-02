package org.example.notesharing.exception;

import org.springframework.http.HttpStatus;

/** A well-formed request that the domain refuses, e.g. a note ending before it starts. */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(HttpStatus.BAD_REQUEST, "Invalid request", message);
    }
}

package org.example.notesharing.exception;

import org.springframework.http.HttpStatus;

/**
 * A uniqueness rule was violated. The title names the field rather than saying
 * "already exists", so the client knows which value to change.
 */
public class ConflictException extends ApiException {

    public ConflictException(String field, Object value) {
        super(HttpStatus.CONFLICT, field + " already in use",
            field + " '" + value + "' is already taken");
    }
}

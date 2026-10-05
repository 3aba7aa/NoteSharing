package org.example.notesharing.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base for the application's own failures. Carries the status so the handler stays a mapping. */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    protected ApiException(HttpStatus status, String title, String message) {
        super(message);
        this.status = status;
        this.title = title;
    }
}

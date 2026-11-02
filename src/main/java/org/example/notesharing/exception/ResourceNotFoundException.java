package org.example.notesharing.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, resource + " not found",
            resource + " " + id + " does not exist");
    }

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "Not found", message);
    }
}

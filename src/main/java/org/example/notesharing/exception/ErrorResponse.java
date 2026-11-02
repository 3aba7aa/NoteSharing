package org.example.notesharing.exception;

import java.time.Instant;

public record ErrorResponse(
        String code,
        String message,
        int status,
        Instant timestamp
) {
}

package org.example.notesharing.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Uniform success envelope.
 *
 * @param data      the payload; omitted from JSON when null
 * @param message   short human-readable outcome, e.g. "Registered"
 * @param timestamp when the response was produced
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, String message, Instant timestamp) {

    public static <T> ApiResponse<T> of(T data, String message) {
        return new ApiResponse<>(data, message, Instant.now());
    }

    public static ApiResponse<Void> of(String message) {
        return new ApiResponse<>(null, message, Instant.now());
    }
}

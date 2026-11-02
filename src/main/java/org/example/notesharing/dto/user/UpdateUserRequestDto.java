package org.example.notesharing.dto.user;

import jakarta.validation.constraints.Size;

/**
 * Profile update. Every field is optional; a null field is left unchanged, so a client can
 * send just the one value it wants to change.
 */
public record UpdateUserRequestDto(
    @Size(min = 3, max = 50, message = "must be 3-50 characters") String username) {
}

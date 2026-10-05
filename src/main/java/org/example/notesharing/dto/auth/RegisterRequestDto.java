package org.example.notesharing.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestDto(
    @NotBlank(message = "is required")
    @Size(min = 3, max = 50, message = "must be 3-50 characters")
    String username,

    @NotBlank(message = "is required")
    @Email(message = "must be well-formed")
    String email,

    // 72 bytes is BCrypt's limit; anything longer is silently truncated, which would make
    // "correct password" ambiguous.
    @NotBlank(message = "is required")
    @Size(min = 8, max = 72, message = "must be 8-72 characters")
    String password) {
}

package org.example.notesharing.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDto(
    @NotBlank(message = "is required") String email,
    @NotBlank(message = "is required") String password) {
}

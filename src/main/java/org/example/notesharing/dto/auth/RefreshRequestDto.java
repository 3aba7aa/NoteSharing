package org.example.notesharing.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequestDto(
    @NotBlank(message = "is required") String refreshToken) {
}

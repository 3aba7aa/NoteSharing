package org.example.notesharing.dto.user;

import org.example.notesharing.enums.Gender;

import java.time.LocalDate;

/**
 * Profile view. Deliberately has no password or hash field, so no code path can serialize one
 * by accident — the leak is a compile error rather than a review item.
 */
public record UserResponseDto(
    Long id,
    String username,
    String email,
    LocalDate bod,
    Gender gender) {
}

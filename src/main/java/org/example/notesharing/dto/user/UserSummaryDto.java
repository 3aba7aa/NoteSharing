package org.example.notesharing.dto.user;

/** Compact user view for listings: enough to render a name, nothing sensitive. */
public record UserSummaryDto(
    Long id,
    String username) {
}

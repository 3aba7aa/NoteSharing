package org.example.notesharing.dto.note;

import org.example.notesharing.enums.NoteType;
import org.example.notesharing.enums.NoteVisibility;
import org.example.notesharing.dto.user.UserSummaryDto;

import java.time.LocalDateTime;

/**
 * Compact note view for list results. Omits {@code content}, which can be large and is not
 * needed to render a row, so a page of 20 notes does not carry 20 full bodies.
 */
public record NoteSummaryDto(
    Long id,
    String title,
    NoteType type,
    NoteVisibility visibility,
    LocalDateTime startDate,
    LocalDateTime endDate,
    LocalDateTime createdAt,
    LocalDateTime modifiedAt,
    UserSummaryDto owner,
    String myPermission) {
}

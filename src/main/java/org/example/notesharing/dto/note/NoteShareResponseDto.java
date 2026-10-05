package org.example.notesharing.dto.note;

import org.example.notesharing.dto.user.UserSummaryDto;
import org.example.notesharing.enums.NotePermission;

/** One recipient of a shared note. */
public record NoteShareResponseDto(
    Long id,
    Long noteId,
    UserSummaryDto user,
    NotePermission permission) {
}

package org.example.notesharing.dto.note;

import jakarta.validation.constraints.NotNull;
import org.example.notesharing.enums.NotePermission;

/**
 * @param userId recipient of the share
 * @param permission what the recipient may do; EDIT also grants read
 */
public record ShareNoteRequestDto(
    @NotNull(message = "is required")
    Long userId,

    @NotNull(message = "is required")
    NotePermission permission) {
}

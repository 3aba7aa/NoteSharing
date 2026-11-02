package org.example.notesharing.dto.note;

import jakarta.validation.constraints.NotNull;
import org.example.notesharing.enums.NotePermission;

/**
 * Permission change on an existing share. The recipient is addressed by the path, so the body
 * carries only the one thing that can change.
 */
public record UpdateShareRequestDto(
    @NotNull(message = "is required")
    NotePermission permission) {
}

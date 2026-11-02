package org.example.notesharing.dto.note;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.example.notesharing.enums.NoteType;
import org.example.notesharing.enums.NoteVisibility;

import java.time.LocalDateTime;

/**
 * @param notifyThreshold minutes of lead time before {@code startDate} to notify the owner;
 *                        {@code null} means no reminder is wanted
 */
public record CreateNoteRequestDto(
    @Size(max = 255, message = "must be at most 255 characters")
    String title,

    String content,

    @NotNull(message = "is required")
    NoteType type,

    @NotNull(message = "is required")
    NoteVisibility visibility,

    LocalDateTime startDate,

    LocalDateTime endDate,

    @PositiveOrZero(message = "must not be negative")
    Integer notifyThreshold) {
}

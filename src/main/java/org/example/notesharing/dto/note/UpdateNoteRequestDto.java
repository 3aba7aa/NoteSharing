package org.example.notesharing.dto.note;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.example.notesharing.enums.NoteType;
import org.example.notesharing.enums.NoteVisibility;

import java.time.LocalDateTime;

/**
 * Partial update. Every field is optional and a null field is left as-is, so a client sends
 * only what it changes.
 *
 * <p>The trade-off is that a field cannot be cleared back to null by this endpoint: omitting
 * {@code endDate} and sending {@code endDate: null} are the same request. Use a dedicated
 * clear operation if that ever matters.
 */
public record UpdateNoteRequestDto(
    @Size(max = 255, message = "must be at most 255 characters")
    String title,

    String content,

    NoteType type,

    NoteVisibility visibility,

    LocalDateTime startDate,

    LocalDateTime endDate,

    @PositiveOrZero(message = "must not be negative")
    Integer notifyThreshold) {
}

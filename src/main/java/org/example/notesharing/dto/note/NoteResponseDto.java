package org.example.notesharing.dto.note;

import org.example.notesharing.enums.NoteType;
import org.example.notesharing.enums.NoteVisibility;
import org.example.notesharing.dto.user.UserSummaryDto;

import java.time.LocalDateTime;

/**
 * Full note view, returned to the owner and to anyone the note is shared with.
 *
 * @param myPermission what the requesting user may do with this note: EDIT for the owner and
 *                     for EDIT shares, VIEW for VIEW shares. The client uses it to decide
 *                     whether to render edit controls, so the server does not have to guess.
 */
public record NoteResponseDto(
    Long id,
    String title,
    String content,
    NoteType type,
    NoteVisibility visibility,
    LocalDateTime startDate,
    LocalDateTime endDate,
    Integer notifyThreshold,
    LocalDateTime createdAt,
    LocalDateTime modifiedAt,
    UserSummaryDto owner,
    String myPermission) {
}

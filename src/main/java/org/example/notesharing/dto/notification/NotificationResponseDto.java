package org.example.notesharing.dto.notification;

import org.example.notesharing.dto.user.UserSummaryDto;
import org.example.notesharing.enums.NotificationType;

import java.time.LocalDateTime;

/**
 * One notification, addressed to the caller.
 *
 * <p>The related note is exposed by id only, not inlined. A notification about a shared note
 * can outlive the share, and a client that fetched the note would then get a 404 on a row it
 * still displays; the id lets it decide what to render without that.
 */
public record NotificationResponseDto(
    Long id,
    String message,
    NotificationType type,
    LocalDateTime issueDate,
    boolean read,
    UserSummaryDto notifier,
    Long noteId) {
}

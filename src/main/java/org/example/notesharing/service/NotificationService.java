package org.example.notesharing.service;

import lombok.RequiredArgsConstructor;
import org.example.notesharing.dto.common.PageResponse;
import org.example.notesharing.dto.notification.NotificationResponseDto;
import org.example.notesharing.dto.user.UserSummaryDto;
import org.example.notesharing.entity.Notification;
import org.example.notesharing.entity.Note;
import org.example.notesharing.entity.User;
import org.example.notesharing.enums.NotificationType;
import org.example.notesharing.repository.NotificationRepository;
import org.example.notesharing.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Reading notifications, and the one place that writes them.
 *
 * <p>Creation is {@link Propagation#REQUIRES_NEW} so a notification is not rolled back with
 * the action that caused it, and so failing to notify never fails the operation the user
 * actually asked for.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponseDto> list(Long userId, Boolean read,
                                                     NotificationType type, Pageable pageable) {
        Page<Notification> page = notifications.findForUser(userId, read, type, pageable);
        return PageResponse.from(page, NotificationService::toResponse);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notifications.countUnread(userId);
    }

    /**
     * Marks one notification read.
     *
     * <p>Already-read is treated as success: the caller's intent holds either way, and
     * answering 409 would make a client that retries on failure loop forever.
     */
    @Transactional
    public void markRead(Long notificationId, Long userId) {
        notifications.markRead(notificationId, userId);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notifications.markAllRead(userId);
    }

    /**
     * Records that {@code notifier} acted on something of {@code notified}'s interest.
     *
     * <p>{@code notifier} is null for events with no actor, such as a reminder the system
     * raises on its own.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(User notifier, User notified, NotificationType type, String message,
                       Note note) {
        if (notified == null) {
            return;
        }
        // Respect the recipient's opt-out. Sending anyway would make notificationEnabled
        // meaningless, and it is the only control the user has over what reaches them.
        if (isMuted(notified.getId())) {
            return;
        }

        Notification notification = new Notification();
        notification.setNotifier(notifier);
        notification.setNotified(notified);
        notification.setType(type);
        notification.setMessage(message);
        notification.setIssueDate(LocalDateTime.now());
        notification.setRead(false);
        notification.setNote(note);
        notifications.save(notification);
    }

    /**
     * Deletes the notification {@code notifier} raised for {@code notified}, when the thing it
     * referred to has been dealt with.
     *
     * <p>Read or not. Reading a notification says something about the reader, not about
     * whether the thing still exists, so a read row is exactly as stale as an unread one.
     */
    @Transactional
    public void dismiss(User notifier, User notified, NotificationType type) {
        if (notifier == null || notified == null) {
            return;
        }
        notifications.dismiss(notifier.getId(), notified.getId(), type);
    }

    private boolean isMuted(Long userId) {
        return users.findSettingsById(userId)
            .map(settings -> Boolean.FALSE.equals(settings.getNotificationEnabled()))
            .orElse(false);
    }

    private static NotificationResponseDto toResponse(Notification n) {
        return new NotificationResponseDto(
            n.getId(),
            n.getMessage(),
            n.getType(),
            n.getIssueDate(),
            Boolean.TRUE.equals(n.getRead()),
            n.getNotifier() == null ? null
                : new UserSummaryDto(n.getNotifier().getId(), n.getNotifier().getUsername()),
            n.getNote() == null ? null : n.getNote().getId());
    }

}

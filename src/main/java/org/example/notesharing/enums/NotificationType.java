package org.example.notesharing.enums;

public enum NotificationType {
    /** Someone asked to be friends; the recipient can accept or reject. */
    FRIEND_REQUEST,

    /** A note was shared with the recipient. */
    NOTE_SHARED,

    /**
     * Raised by the system with no actor, for a note whose start date is approaching.
     * {@code notifier} is null for this type.
     */
    NOTE_DUE,

    MESSAGE_REQUEST
}

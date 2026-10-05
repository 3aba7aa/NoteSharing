package org.example.notesharing.dto.settings;

import org.example.notesharing.enums.Theme;

/**
 * The caller's settings.
 *
 * @param userId             whose settings these are
 * @param theme              resolved theme; {@code SYSTEM} means follow the client
 * @param twoFactorEnabled   a stored preference with no effect: two-factor login is not
 *                           implemented, so this is never true in practice and setting it
 *                           true grants nothing. Exposed because the column exists in the
 *                           schema and the spec calls for it, not because it does anything.
 * @param notificationEnabled whether notifications are raised for this user. This one is
 *                           enforced: a friend request or note share to a user who has turned
 *                           notifications off creates no row.
 */
public record SettingsResponseDto(
        Long userId,
        Theme theme,
        Boolean twoFactorEnabled,
        Boolean notificationEnabled) {
}
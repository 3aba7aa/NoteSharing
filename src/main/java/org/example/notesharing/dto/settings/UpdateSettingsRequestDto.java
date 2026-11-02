package org.example.notesharing.dto.settings;

import org.example.notesharing.enums.Theme;

/**
 * Partial update of the caller's settings.
 *
 * <p>A null field means "leave unchanged", matching {@code PUT /notes/{id}}. The practical
 * consequence is that a boolean preference cannot be turned back off by omission: it must be
 * sent as {@code false} explicitly. That is worth stating because the alternative reading --
 * null means false -- would silently switch settings off for any client that builds its
 * request body by spreading an object it did not mean to change.
 *
 * <p>An unrecognised {@code theme} fails to bind and is answered with a 400, so the stored
 * value can never be something the server cannot read back.
 *
 * @param theme               new theme, or null to keep the current one
 * @param twoFactorEnabled    stored preference only; no second factor is enforced
 * @param notificationEnabled whether notifications are raised for this user
 */
public record UpdateSettingsRequestDto(
        Theme theme,
        Boolean twoFactorEnabled,
        Boolean notificationEnabled) {
}
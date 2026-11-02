package org.example.notesharing.service;

import lombok.RequiredArgsConstructor;
import org.example.notesharing.dto.settings.SettingsResponseDto;
import org.example.notesharing.dto.settings.UpdateSettingsRequestDto;
import org.example.notesharing.entity.User;
import org.example.notesharing.entity.UserSettings;
import org.example.notesharing.enums.Theme;
import org.example.notesharing.exception.ResourceNotFoundException;
import org.example.notesharing.repository.UserRepository;
import org.example.notesharing.repository.UserSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The caller's own preferences.
 *
 * <p>No method takes a user id from the caller. Settings are addressed through the
 * authenticated principal alone, so there is no route that can read or change another
 * account's preferences.
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final UserSettingsRepository settings;
    private final UserRepository users;

    /**
     * Current settings, or the registration defaults when the row is missing.
     *
     * <p>Does not write. Registration creates the row, so this only applies to a user created
     * before the row existed; reporting defaults is enough, and a read that writes would make
     * a plain {@code GET} surprising.
     */
    @Transactional(readOnly = true)
    public SettingsResponseDto get(Long userId) {
        return settings.findById(userId)
            .map(SettingsService::toResponse)
            .orElseGet(() -> defaults(userId));
    }

    /**
     * Applies a partial update: null fields are left as they are.
     *
     * <p>Creates the row if it is missing rather than failing, so a client can always set a
     * preference without having to know whether the row exists.
     */
    @Transactional
    public SettingsResponseDto update(Long userId, UpdateSettingsRequestDto request) {
        User user = users.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        UserSettings current = settings.findById(userId).orElseGet(() -> {
            UserSettings fresh = new UserSettings();
            fresh.setUser(user);
            return fresh;
        });

        if (request.theme() != null) {
            current.setTheme(request.theme());
        }
        if (request.twoFactorEnabled() != null) {
            current.setTwoFactorEnabled(request.twoFactorEnabled());
        }
        if (request.notificationEnabled() != null) {
            current.setNotificationEnabled(request.notificationEnabled());
        }

        // A row created here starts from all-null columns, so fill in anything the request
        // did not mention. Without this the user would be left with a row whose theme is
        // null, and the next read would have to invent a value for it.
        if (current.getTheme() == null) {
            current.setTheme(Theme.SYSTEM);
        }
        if (current.getTwoFactorEnabled() == null) {
            current.setTwoFactorEnabled(false);
        }
        if (current.getNotificationEnabled() == null) {
            current.setNotificationEnabled(true);
        }

        return toResponse(settings.save(current));
    }

    private static SettingsResponseDto defaults(Long userId) {
        return new SettingsResponseDto(userId, Theme.SYSTEM, false, true);
    }

    private static SettingsResponseDto toResponse(UserSettings s) {
        return new SettingsResponseDto(
            s.getUserId() == null ? s.getUser().getId() : s.getUserId(),
            s.getTheme(),
            s.getTwoFactorEnabled(),
            s.getNotificationEnabled());
    }
}
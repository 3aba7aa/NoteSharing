package org.example.notesharing.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.notesharing.dto.common.ApiResponse;
import org.example.notesharing.dto.settings.SettingsResponseDto;
import org.example.notesharing.dto.settings.UpdateSettingsRequestDto;
import org.example.notesharing.dto.user.UpdateUserRequestDto;
import org.example.notesharing.dto.user.UserResponseDto;
import org.example.notesharing.dto.user.UserSummaryDto;
import org.example.notesharing.security.JwtPrincipal;
import org.example.notesharing.service.SettingsService;
import org.example.notesharing.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SettingsService settingsService;

    @GetMapping("/me")
    public ApiResponse<UserResponseDto> me(@AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.of(userService.getById(principal.userId()), "Profile");
    }

    @PutMapping("/me")
    public ApiResponse<UserResponseDto> updateMe(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody UpdateUserRequestDto request) {
        return ApiResponse.of(userService.update(principal.userId(), request), "Profile updated");
    }

    /**
     * Preferences for the caller. Addressed through {@code /me} rather than a user id, so
     * there is no route from which one account can read another's settings.
     */
    @GetMapping("/me/settings")
    public ApiResponse<SettingsResponseDto> getSettings(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.of(settingsService.get(principal.userId()), "Settings");
    }

    @PutMapping("/me/settings")
    public ApiResponse<SettingsResponseDto> updateSettings(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestBody UpdateSettingsRequestDto request) {
        return ApiResponse.of(
            settingsService.update(principal.userId(), request), "Settings updated");
    }

    @GetMapping("/{id}")
    public ApiResponse<UserSummaryDto> byId(@PathVariable Long id) {
        UserResponseDto profile = userService.getById(id);
        return ApiResponse.of(
            new UserSummaryDto(profile.id(), profile.username()),
            "User");
    }

    /**
     * @param q mandatory search term. Declared {@code required = false} so a missing
     *          parameter reaches the service and is rejected as a 400 business-rule
     *          violation, rather than Spring's own 500 for an unbound parameter.
     */
    @GetMapping("/search")
    public ApiResponse<List<UserSummaryDto>> search(
            @RequestParam(name = "q", required = false) String query) {
        return ApiResponse.of(userService.search(query), "Search results");
    }
}

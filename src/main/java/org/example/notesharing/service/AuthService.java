package org.example.notesharing.service;

import lombok.RequiredArgsConstructor;
import org.example.notesharing.dto.auth.LoginRequestDto;
import org.example.notesharing.dto.auth.RefreshRequestDto;
import org.example.notesharing.dto.auth.RegisterRequestDto;
import org.example.notesharing.dto.auth.TokenPairDto;
import org.example.notesharing.dto.user.UserResponseDto;
import org.example.notesharing.entity.RefreshToken;
import org.example.notesharing.entity.User;
import org.example.notesharing.entity.UserSettings;
import org.example.notesharing.enums.Theme;
import org.example.notesharing.exception.ConflictException;
import org.example.notesharing.exception.ErrorEnum;
import org.example.notesharing.exception.ResourceNotFoundException;
import org.example.notesharing.exception.SystemException;
import org.example.notesharing.exception.UnauthorizedException;
import org.example.notesharing.repository.RefreshTokenRepository;
import org.example.notesharing.repository.UserRepository;
import org.example.notesharing.repository.UserSettingsRepository;
import org.example.notesharing.security.JwtService;
import org.example.notesharing.security.TokenType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final UserSettingsRepository settings;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public TokenPairDto register(RegisterRequestDto request) {
        String email = request.email().trim().toLowerCase();
        String username = request.username().trim();

        // Case-insensitive so Bob@x.com and bob@x.com cannot become two accounts.
        if (users.existsByEmailIgnoreCase(email)) {
            throw new SystemException(ErrorEnum.EMAIL_ALREADY_EXISTS);
        }
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new SystemException(ErrorEnum.USERNAME_ALREADY_EXISTS);
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setHashedPassword(passwordEncoder.encode(request.password()));
        user.setCreatedAt(LocalDateTime.now());

        User saved = users.save(user);

        UserSettings userSettings = new UserSettings();
        userSettings.setUser(saved);
        userSettings.setTheme(Theme.SYSTEM);
        userSettings.setTwoFactorEnabled(false);
        userSettings.setNotificationEnabled(true);
        settings.save(userSettings);

        return issueTokens(saved);
    }

    @Transactional
    public TokenPairDto login(LoginRequestDto request) {
        User user = users.findByEmailIgnoreCase(request.email().trim())
            .orElse(null);

        // One message for both cases. Distinguishing them would confirm which emails exist.
        if (user == null || !passwordEncoder.matches(request.password(), user.getHashedPassword())) {
            throw new SystemException(ErrorEnum.INVALID_CREDENTIALS);
        }
        return issueTokens(user);
    }

    @Transactional
    public TokenPairDto refresh(RefreshRequestDto request) {
        JwtService.ParsedToken parsed = jwtService
            .parseAs(request.refreshToken(), TokenType.REFRESH)
            .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid or expired"));

        if (parsed.tokenId() == null) {
            throw new UnauthorizedException("Refresh token is invalid or expired");
        }

        RefreshToken stored = refreshTokens.findByTokenId(parsed.tokenId().toString())
            .orElseThrow(() -> new UnauthorizedException("Refresh token is not recognised"));

        // A revoked token being presented again means it was captured; refuse it rather than
        // quietly issuing a fresh pair.
        if (Boolean.TRUE.equals(stored.getRevoked())) {
            throw new UnauthorizedException("Refresh token has been revoked");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token has expired");
        }

        User user = users.findById(stored.getUser().getId())
            .orElseThrow(() -> new ResourceNotFoundException("User", stored.getUser().getId()));

        stored.setRevoked(true);
        refreshTokens.save(stored);

        return issueTokens(user);
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokens.revokeAllForUser(userId);
    }

    private TokenPairDto issueTokens(User user) {
        UUID tokenId = UUID.randomUUID();

        RefreshToken stored = new RefreshToken();
        stored.setUser(user);
        stored.setTokenId(tokenId.toString());
        stored.setExpiresAt(LocalDateTime.now().plus(jwtService.getRefreshTokenTtl()));
        stored.setRevoked(false);
        refreshTokens.save(stored);

        return new TokenPairDto(
            "Bearer",
            jwtService.generateAccessToken(user.getId(), user.getUsername()),
            jwtService.generateRefreshToken(user.getId(), tokenId),
            jwtService.getExpiresInSeconds(TokenType.ACCESS));
    }
}

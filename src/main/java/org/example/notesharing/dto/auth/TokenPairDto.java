package org.example.notesharing.dto.auth;

/**
 * @param expiresInSeconds lifetime of the access token; the client should refresh before
 *                         this elapses rather than waiting for a 401
 */
public record TokenPairDto(
    String tokenType,
    String accessToken,
    String refreshToken,
    long expiresInSeconds) {
}

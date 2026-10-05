package org.example.notesharing.security;

/** The authenticated caller. Carries the user id so controllers never re-read the entity. */
public record JwtPrincipal(Long userId, String username) {
}

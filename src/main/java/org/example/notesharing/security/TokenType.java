package org.example.notesharing.security;

/** Distinguishes the two token kinds so a refresh token cannot be used as a bearer credential. */
public enum TokenType {
    ACCESS,
    REFRESH
}

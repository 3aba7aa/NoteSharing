package org.example.notesharing.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.example.notesharing.config.JwtProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and validates HS256 tokens.
 *
 * <p>Every parse failure returns {@link Optional#empty()} rather than throwing: callers only
 * ever need to know whether a token is usable, and a malformed header from the internet is an
 * ordinary event, not an exceptional one.
 */
@Slf4j
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_TOKEN_ID = "jti";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        properties.validate();
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String username) {
        return generate(userId, username, null, properties.getAccessTokenTtl(), TokenType.ACCESS);
    }

    public String generateRefreshToken(Long userId, UUID tokenId) {
        return generate(userId, null, tokenId, properties.getRefreshTokenTtl(), TokenType.REFRESH);
    }

    /** Test seam: issues an already-expired token so expiry handling can be exercised. */
    public String generateAccessTokenWithTtl(Long userId, String username, Duration ttl) {
        return generate(userId, username, null, ttl, TokenType.ACCESS);
    }

    private String generate(Long userId, String username, UUID tokenId, Duration ttl, TokenType type) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
            .subject(String.valueOf(userId))
            .issuer(properties.getIssuer())
            .claim(CLAIM_TYPE, type.name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)));
        if (username != null) {
            builder.claim(CLAIM_USERNAME, username);
        }
        if (tokenId != null) {
            builder.claim(CLAIM_TOKEN_ID, tokenId.toString());
        }
        return builder.signWith(key).compact();
    }

    public Optional<ParsedToken> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return Optional.of(toParsed(claims));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected token: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parses only if the token is of the expected type. This is what stops a refresh token
     * from being replayed as an access token.
     */
    public Optional<ParsedToken> parseAs(String token, TokenType required) {
        return parse(token).filter(parsed -> parsed.type() == required);
    }

    public long getExpiresInSeconds(TokenType type) {
        Duration ttl = type == TokenType.ACCESS
            ? properties.getAccessTokenTtl()
            : properties.getRefreshTokenTtl();
        return ttl.toSeconds();
    }

    public Duration getAccessTokenTtl() {
        return properties.getAccessTokenTtl();
    }

    public Duration getRefreshTokenTtl() {
        return properties.getRefreshTokenTtl();
    }

    private static ParsedToken toParsed(Claims claims) {
        String type = claims.get(CLAIM_TYPE, String.class);
        String rawTokenId = claims.get(CLAIM_TOKEN_ID, String.class);
        return new ParsedToken(
            Long.valueOf(claims.getSubject()),
            rawTokenId == null ? null : UUID.fromString(rawTokenId),
            type == null ? null : TokenType.valueOf(type),
            claims.get(CLAIM_USERNAME, String.class));
    }

    public record ParsedToken(Long userId, UUID tokenId, TokenType type, String username) {
    }
}

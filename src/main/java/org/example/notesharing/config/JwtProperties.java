package org.example.notesharing.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * JWT settings bound from {@code app.jwt.*}.
 *
 * <p>The secret is deliberately not given a default. A silently-defaulted secret would let
 * the application start in production signing tokens with a value committed to the repository,
 * which is the kind of mistake nobody notices until it is exploited.
 */
@Getter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** HS256 needs at least 256 bits of key material. */
    static final int MIN_SECRET_BYTES = 32;

    private final String secret;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final String issuer;

    public JwtProperties(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.access-token-ttl:PT15M}") Duration accessTokenTtl,
            @Value("${app.jwt.refresh-token-ttl:P7D}") Duration refreshTokenTtl,
            @Value("${app.jwt.issuer:notesharing}") String issuer) {
        this.secret = secret;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
        this.issuer = issuer;
    }

    /**
     * Fails fast on settings that would make the deployment insecure or unusable.
     * Called explicitly by the security configuration rather than in the constructor, so
     * tests can build the object with bad values and assert on {@link #validate()}.
     */
    public void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "app.jwt.secret is not set. Provide JWT_SECRET rather than relying on a default.");
        }
        if (secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes for HS256.");
        }
        if (accessTokenTtl.compareTo(refreshTokenTtl) >= 0) {
            throw new IllegalStateException(
                "app.jwt.access-token-ttl must be shorter than app.jwt.refresh-token-ttl; "
                    + "an access token that outlives its refresh token cannot be renewed.");
        }
    }
}

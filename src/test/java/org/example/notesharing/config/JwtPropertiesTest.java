package org.example.notesharing.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The secret guard is the one piece of this task with real logic, so it gets a real test.
 * Everything else here is YAML and was verified by booting the context.
 */
class JwtPropertiesTest {

    private static final String GOOD_SECRET = "a-secret-that-is-at-least-32-bytes-long-xyz";

    @Test
    void rejectsBlankSecret() {
        JwtProperties props = new JwtProperties(
            "  ", Duration.ofMinutes(15), Duration.ofDays(7), "notesharing");

        assertThatThrownBy(props::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("app.jwt.secret");
    }

    @Test
    void rejectsSecretShorterThanHs256Requires() {
        JwtProperties props = new JwtProperties(
            "too-short", Duration.ofMinutes(15), Duration.ofDays(7), "notesharing");

        assertThatThrownBy(props::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("32");
    }

    @Test
    void acceptsAValidSecret() {
        JwtProperties props = new JwtProperties(
            GOOD_SECRET, Duration.ofMinutes(15), Duration.ofDays(7), "notesharing");

        assertThatCode(props::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsAccessTokenTtlLongerThanRefreshTokenTtl() {
        JwtProperties props = new JwtProperties(
            GOOD_SECRET, Duration.ofDays(30), Duration.ofDays(7), "notesharing");

        assertThatThrownBy(props::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("access");
    }
}

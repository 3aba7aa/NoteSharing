package org.example.notesharing.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
class ProfileConfigTest {

    @Autowired
    Environment env;

    @Test
    void devUsesH2AndCreateDrop() {
        assertThat(env.getProperty("spring.datasource.url")).startsWith("jdbc:h2:");
        assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
    }

    @Test
    void jwtPropertiesBindFromConfiguration() {
        JwtProperties props = new JwtProperties(
            env.getProperty("app.jwt.secret"),
            java.time.Duration.parse(env.getProperty("app.jwt.access-token-ttl")),
            java.time.Duration.parse(env.getProperty("app.jwt.refresh-token-ttl")),
            env.getProperty("app.jwt.issuer"));

        assertThat(props.getAccessTokenTtl()).isEqualTo(java.time.Duration.ofMinutes(15));
        assertThat(props.getRefreshTokenTtl()).isEqualTo(java.time.Duration.ofDays(7));
    }
}

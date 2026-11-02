package org.example.notesharing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the whole context to prove the wiring is satisfiable. Uses the test profile, which
 * supplies the JWT secret and a Testcontainers PostgreSQL, so this covers the security
 * filter chain and the entity manager factory rather than just bean registration.
 */
@SpringBootTest
@ActiveProfiles("test")
class NoteSharingApplicationTests {

    @Test
    void contextLoads() {
    }

}

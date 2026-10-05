package org.example.notesharing.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;

/**
 * Forces Flyway to migrate before Hibernate validates.
 *
 * <p>Hibernate's {@code ddl-auto=validate} runs while the EntityManagerFactory is being
 * built, which by default is before Flyway has created any table — so validation reports
 * "missing table" against an empty schema. {@code spring.jpa.defer-datasource-initialization}
 * does not fix this: it orders script-based initialization, not Flyway.
 *
 * <p>Declaring a dependency on {@code flywayInitializer} is what actually sequences the two.
 *
 * <p>Restricted to the {@code test} profile on purpose. This class sits under the application's
 * own base package, so component scanning picks it up in every context — and the dev profile
 * sets {@code spring.flyway.enabled=false}, which means there is no {@code flywayInitializer}
 * bean to depend on and startup fails. The ordering is only needed where Flyway actually runs.
 */
@Configuration
@Profile("test")
@DependsOn("flywayInitializer")
public class FlywayTestConfig {
}

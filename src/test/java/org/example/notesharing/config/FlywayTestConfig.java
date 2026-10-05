package org.example.notesharing.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/**
 * Forces Flyway to migrate before Hibernate validates.
 *
 * <p>Hibernate's {@code ddl-auto=validate} runs while the EntityManagerFactory is being
 * built, which by default is before Flyway has created any table — so validation reports
 * "missing table" against an empty schema. {@code spring.jpa.defer-datasource-initialization}
 * does not fix this: it orders script-based initialization, not Flyway.
 *
 * <p>Declaring a dependency on {@code flywayInitializer} is what actually sequences the two.
 * Imported by the {@code *IT} tests that assert against a migrated schema.
 */
@Configuration
@DependsOn("flywayInitializer")
public class FlywayTestConfig {
}

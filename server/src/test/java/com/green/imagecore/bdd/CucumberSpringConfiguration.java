package com.green.imagecore.bdd;

import com.green.imagecore.ImagecoreApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Global Spring configuration for Cucumber BDD tests.
 * This class acts as the "Glue" that initializes the Spring Boot ApplicationContext
 * for the entire Cucumber test suite, ensuring that step definitions have access
 * to dependency injection and the persistence layer.
 */
@CucumberContextConfiguration
@SpringBootTest(classes = ImagecoreApplication.class)
@ActiveProfiles("test") // Loads application-test.yml to align with the test environment
@Testcontainers         // Enables the Testcontainers extension for database orchestration
public class CucumberSpringConfiguration {

    /**
     * Shared PostgreSQL container instance used across all Cucumber scenarios.
     * Using a real PostgreSQL instance in Docker ensures that BDD tests validate
     * database-specific logic (like Flyway migrations and JPA queries) in a
     * production-like environment.
     */
    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("imagecore_test")
                    .withUsername("test")
                    .withPassword("test");

    // Manually trigger the container start sequence before the Spring context loads.
    // This ensures the database is ready to receive connections during bean initialization.
    static {
        postgres.start();
    }

    /**
     * Dynamically overrides Spring properties with values from the running Docker container.
     * Since Testcontainers assigns a random ephemeral port at startup to avoid conflicts,
     * this method ensures that HikariCP and Flyway connect to the correct dynamic URL.
     *
     * @param registry The registry used to add or override environment properties.
     */
    @DynamicPropertySource
    static void registerDataSourceProps(DynamicPropertyRegistry registry) {
        // Map the dynamic container properties to standard Spring Boot datasource keys
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Ensure Flyway uses the same dynamic connection for database migrations
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }
}

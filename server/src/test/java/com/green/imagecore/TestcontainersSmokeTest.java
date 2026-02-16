package com.green.imagecore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Diagnostic "Smoke Test" for Testcontainers integration.
 * This class verifies that the Docker-based PostgreSQL environment is correctly
 * orchestrated and that Spring Boot is dynamically routing traffic to the
 * containerized instance rather than a local host database.
 */
@SpringBootTest
@ActiveProfiles("test") // Ensures settings from application-test.yml are applied
@Testcontainers         // Manages the automatic startup and shutdown of Docker containers
class TestcontainersSmokeTest {

    /**
     * Ephemeral PostgreSQL instance.
     * By using a random port and a clean image (postgres:16-alpine), we ensure
     * a predictable "blank slate" for every test execution, which is vital
     * for CI/CD reliability and HIPAA-compliant data isolation.
     *
     * @ServiceConnection handles the dynamic injection of the container's
     * randomized JDBC URL into the Spring environment.
     */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("imagecore_test")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired
    private DataSource dataSource;

    /**
     * Validates the database connectivity and network isolation.
     * This test ensures that the application is truly connected to the
     * Testcontainer by verifying the JDBC metadata and checking that
     * it is not accidentally communicating with a local Postgres instance (port 5432).
     */
    @Test
    void connectsToDatabase() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            System.out.println("DB URL = " + url);

            // Verify the connection object is valid and active
            assertNotNull(conn);
            assertFalse(conn.isClosed());

            // Security/Isolation Check:
            // Testcontainers uses random high-range ports. If the URL contains "5432",
            // it indicates a misconfiguration where the test is hitting a local database.
            assertFalse(url.contains("localhost:5432"),
                    "Still pointing at local Postgres!");
        }
    }
}

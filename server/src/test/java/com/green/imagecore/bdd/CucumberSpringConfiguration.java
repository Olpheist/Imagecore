package com.green.imagecore.bdd;

import com.green.imagecore.ImagecoreApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.medicalimaging.MedicalImagingClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Global Spring configuration for Cucumber BDD tests.
 * This class serves as the bridge between the Cucumber execution engine and the
 * Spring Boot ApplicationContext. It ensures that all BDD step definitions
 * share a single, consistent test environment.
 */
@CucumberContextConfiguration
@SpringBootTest(classes = ImagecoreApplication.class)
@ActiveProfiles("test") // Ensures the test" profile settings are active for BDD scenarios
@Testcontainers         // Manages the lifecycle of Docker-based test dependencies
public class CucumberSpringConfiguration {

    /**
     * Managed PostgreSQL container instance.
     * By using a real PostgreSQL instance (matching production version 16), we
     * ensure that BDD scenarios validate the actual SQL dialects and migrations
     * used in the ImageCore platform.
     *
     * @ServiceConnection is a Spring Boot 3.1+ feature that automatically
     * discovers the container's dynamic port and credentials, injecting them into
     * the Spring environment (DataSource and Flyway) without manual property mapping.
     */
    // Mocked with Mockito so tests don’t initialize real AWS clients
    // (avoids needing real credentials or AWS configuration).
    @MockitoBean
    S3Client s3Client;

    @MockitoBean
    S3Presigner s3Presigner;

    @MockitoBean
    EcsClient ecsClient;

    @MockitoBean
    MedicalImagingClient medicalImagingClient;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("imagecore_test")
                    .withUsername("test")
                    .withPassword("test");
}

package com.green.imagecore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.medicalimaging.MedicalImagingClient;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Fundamental smoke test for the ImageCore application.
 * This class verifies that the Spring ApplicationContext can be initialized successfully.
 * It serves as the first line of defense against configuration errors, dependency issues,
 * or breaking changes in the database schema.
 */
@SpringBootTest
@ActiveProfiles("test") // Ensures the test environment uses application-test.yml settings
@Testcontainers         // Orchestrates the lifecycle of Docker containers for testing
class ImagecoreApplicationTests {

	/**
	 * Managed PostgreSQL container instance.
	 * By using Testcontainers, we verify the application's startup against a real
	 * PostgreSQL database rather than an in-memory substitute (like H2), ensuring
	 * that migrations and JPA dialects are compatible with the production environment.
	 *
	 * @MockitoBean used to mock S3Client so the real AWS client is never created during tests
	 * Prevents app from trying to connect to AWS or requiring real credentials
	 *
	 * @ServiceConnection automatically discovers the container and maps its dynamic
	 * port, username, and password to Spring's 'spring.datasource' properties.
	 */
	@MockitoBean
	S3Client s3Client;

	@MockitoBean
	MedicalImagingClient medicalImagingClient;

	@Container
	@ServiceConnection // Automatically wires spring.datasource properties
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	/**
	 * Verifies that the Spring Boot application starts without throwing exceptions.
	 * For this test to pass, all beans must be successfully created, Flyway migrations
	 * must execute against the containerized database, and the security configuration
	 * (including JWT decoding) must be valid.
	 */
	@Test
	void contextLoads() {
		// This test will now pass because it has a valid database connection
	}

}
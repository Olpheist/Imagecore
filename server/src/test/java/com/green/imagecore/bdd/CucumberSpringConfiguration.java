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

@CucumberContextConfiguration
@SpringBootTest(classes = ImagecoreApplication.class)
@ActiveProfiles("test")
@Testcontainers
public class CucumberSpringConfiguration {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("imagecore_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void registerDataSourceProps(DynamicPropertyRegistry registry) {
        // DataSource for JPA/Hikari
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Flyway will use the same datasource by default, but being explicit is fine:
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }
}

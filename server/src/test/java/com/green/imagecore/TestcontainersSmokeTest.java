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

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class TestcontainersSmokeTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("imagecore_test")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired
    private DataSource dataSource;

    @Test
    void connectsToDatabase() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            System.out.println("DB URL = " + url);

            assertNotNull(conn);
            assertFalse(conn.isClosed());

            // prove we are not using the local DB
            assertFalse(url.contains("localhost:5432"),
                    "Still pointing at local Postgres!");
        }
    }
}

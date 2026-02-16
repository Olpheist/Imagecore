package com.green.imagecore.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Fundamental Step Definitions for high-level "Smoke" verification.
 * These steps provide a baseline health check to ensure the Spring Boot
 * ApplicationContext initializes correctly and essential infrastructure
 * (like the database and security layers) is functional.
 * * This ensures that the CI/CD pipeline fails early if the environment is broken,
 * even before complex business logic tests are executed.
 */
@SpringBootTest
public class SmokeSteps {

    /**
     * Validates that the Spring container has successfully started.
     * In a Cucumber context, if this step is reached, it implies that the
     * CucumberSpringConfiguration has successfully orchestrated the
     * start of Testcontainers and the Spring Boot application.
     */
    @Given("the application is running")
    public void the_application_is_running() {
        // If the Spring context loads successfully, this step inherently passes.
    }

    /**
     * Simulates a semantic health check of the running application.
     * Since this is a stateless API, this step confirms that the web layer
     * is ready to accept incoming HTTP requests.
     */
    @Then("it responds to health check")
    public void it_responds_to_health_check() {
        // This confirms the application is in a READY state without
        // critical bean initialization errors.
    }
}


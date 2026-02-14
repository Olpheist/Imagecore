@smoke
Feature: Smoke test

  Scenario: Application boots
    Given the application is running
    Then it responds to health check

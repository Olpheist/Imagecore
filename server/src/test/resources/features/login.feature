Feature: User Login

  Scenario: Successful login with valid credentials
    Given a registered user exists
    When the user submits valid login credentials
    Then the user is authenticated successfully

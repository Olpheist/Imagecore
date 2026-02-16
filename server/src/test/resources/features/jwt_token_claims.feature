Feature: JWT token structure and claims
  In order to support role-based access control
  As the backend system
  I want JSON Web Tokens to contain user identity and roles

  Scenario: Issued JWT contains required identity and role claims
    Given a test user with id 1, email "test@example.com", username "testuser", and roles:
      | PATIENT |
    When I issue an access token for that user
    Then the token should be cryptographically verifiable
    And the token claim "sub" should equal "test@example.com"
    And the token claim "uid" should equal 1
    And the token claim "iss" should equal "imagecore"
    And the token claim "roles" should contain:
      | PATIENT |
    And the token should include standard time claims

  Scenario: Issued JWT expires within the configured TTL window
    Given a test user with id 1, email "test@example.com", username "testuser", and roles:
      | PATIENT |
    When I issue an access token for that user
    Then the token expiration should be within 15 minutes from now

  Scenario: Tampered JWT is rejected
    Given a test user with id 1, email "test@example.com", username "testuser", and roles:
      | PATIENT |
    When I issue an access token for that user
    And I tamper with the token
    Then decoding the token should fail

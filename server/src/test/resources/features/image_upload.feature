Feature: DICOM image upload access control
  Only users with the CLINICIAN role may upload DICOM files.
  Files that are not valid DICOM Part 10 format are rejected regardless of role.

  Scenario: A CLINICIAN can upload a valid DICOM file
    Given I am authenticated as a user with role "CLINICIAN"
    When I upload a valid DICOM file
    Then the upload response status is 201
    And the response body contains an S3 key

  Scenario: A PATIENT cannot upload a DICOM file
    Given I am authenticated as a user with role "PATIENT"
    When I upload a valid DICOM file
    Then the upload response status is 403

  Scenario: An unauthenticated user cannot upload a DICOM file
    Given I am not authenticated for upload
    When I upload a valid DICOM file
    Then the upload response status is 401

  Scenario: A CLINICIAN uploading a non-DICOM file receives a bad request
    Given I am authenticated as a user with role "CLINICIAN"
    When I upload a non-DICOM file
    Then the upload response status is 400

  Scenario: A CLINICIAN uploading an empty file receives a bad request
    Given I am authenticated as a user with role "CLINICIAN"
    When I upload an empty file
    Then the upload response status is 400

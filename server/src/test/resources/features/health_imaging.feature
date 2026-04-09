Feature: HealthImaging import status and image listing
  After uploading a DICOM file, a CLINICIAN can list their images and poll the
  import status of individual uploads. Access is scoped to the authenticated user
  only — images belonging to other users are not visible.

  Scenario: A CLINICIAN can list their uploaded images
    Given I am a CLINICIAN with an uploaded DICOM file
    When I request my image list
    Then the image list response status is 200
    And the image list contains 1 image with status "IN_PROGRESS"

  Scenario: A CLINICIAN with no uploads receives an empty image list
    Given I am a CLINICIAN with no uploaded images
    When I request my image list
    Then the image list response status is 200
    And the image list response is empty

  Scenario: A CLINICIAN can poll the import status of their image
    Given I am a CLINICIAN with an uploaded DICOM file
    And HealthImaging reports the import job as "IN_PROGRESS"
    When I check the import status of my image
    Then the status response is 200
    And the import status in the response is "IN_PROGRESS"

  Scenario: Import status is updated to COMPLETED when HealthImaging finishes
    Given I am a CLINICIAN with an uploaded DICOM file
    And HealthImaging reports the import job as "COMPLETED"
    When I check the import status of my image
    Then the status response is 200
    And the import status in the response is "COMPLETED"

  Scenario: A CLINICIAN requesting status of a nonexistent image receives not found
    Given I am a CLINICIAN with no uploaded images
    When I check the import status of image with ID 99999
    Then the status response is 404

  Scenario: A PATIENT cannot list images
    Given I am a PATIENT
    When I request my image list
    Then the image list response status is 403

  Scenario: An unauthenticated user cannot list images
    Given I am not authenticated for upload
    When I request my image list
    Then the image list response status is 401

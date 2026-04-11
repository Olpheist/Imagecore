Feature: Analysis job submission and report download
  Authenticated users can submit analysis tool jobs for their own DICOM images
  and download the resulting PDF report once the tool has finished.

  Background:
    Given a job user exists with username "job.user"
    And the job user has a DICOM image imported into HealthImaging
    And a tool exists with name "n4-bias-correction" and a task definition ARN

  Scenario: Successfully submit a job for an owned image
    When the job user submits a job for their image using the n4 tool
    Then a job record is created with status SUBMITTED
    And the job record stores an ECS task ARN

  Scenario: Submit fails when the image has no imageSetId
    Given the job user has an image that has not been imported into HealthImaging
    When the job user submits a job for that unimported image
    Then an IllegalStateException is thrown mentioning HealthImaging

  Scenario: Submit fails when the image belongs to another user
    Given another user owns a separate DICOM image
    When the job user submits a job for the other user's image
    Then a ResourceNotFoundException is thrown because the image is not owned by the job user

  Scenario: Report is not available when the tool has not finished
    When the job user submits a job for their image using the n4 tool
    And the report has not been uploaded to S3 yet
    Then requesting the report throws a ResourceNotFoundException mentioning "not yet available"

  Scenario: Report download returns a presigned URL when the report exists
    When the job user submits a job for their image using the n4 tool
    And the report has been uploaded to S3
    Then requesting the report returns a presigned URI

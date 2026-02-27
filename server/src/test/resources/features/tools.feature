Feature: Medical imaging tool management

  Background:
    Given the user is authenticated as a researcher with username "tool.creator"

  Scenario: Retrieve all tools from a populated registry
    Given the following tools exist in the database:
      | name                 | category     | description                     | image_tag                                                  |
      | brain-segmentation   | segmentation | Segments brain MRI regions      | 123456789.dkr.ecr.us-east-1.amazonaws.com/brain-seg:v1.2.0 |
      | lung-nodule-detector | detection    | Detects pulmonary nodules in CT | 123456789.dkr.ecr.us-east-1.amazonaws.com/lung-nd:v2.0.1   |
    When the user retrieves all tools
    Then the tool list contains 2 tools
    And each tool has a non-null name and category

  Scenario: Retrieve all tools returns empty list when none exist
    Given no tools exist in the database
    When the user retrieves all tools
    Then the tool list is empty

  Scenario: Successfully create a tool with all fields
    When the user creates a tool with the following details:
      | name        | spine-segmentation                                         |
      | category    | segmentation                                               |
      | description | Segments vertebrae from lumbar MRI scans                   |
      | imageTag    | 123456789.dkr.ecr.us-east-1.amazonaws.com/spine-seg:v1.0.0 |
    Then the created tool has name "spine-segmentation" and category "segmentation"
    And the created tool has a non-null ID
    And the created tool has imageTag "123456789.dkr.ecr.us-east-1.amazonaws.com/spine-seg:v1.0.0"
    And the tool is persisted in the database

  Scenario: Successfully create a tool with only required fields
    When the user creates a tool with name "minimal-tool" and category "detection"
    Then the created tool has name "minimal-tool" and category "detection"
    And the created tool has a non-null ID
    And the created tool has a null imageTag
    And the tool is persisted in the database

  Scenario: Creating a tool with a duplicate name is rejected
    Given a tool named "brain-segmentation" already exists
    When the user creates a tool with name "brain-segmentation" and category "segmentation"
    Then an IllegalArgumentException is thrown with message containing "brain-segmentation"

  # delete
  Scenario: Successfully delete an existing tool
    Given a tool named "brain-segmentation" already exists
    When the user deletes that tool
    Then the tool is removed from the database

  Scenario: Deleting a non-existent tool throws ResourceNotFoundException
    When the user attempts to delete a tool with id 99999
    Then a ResourceNotFoundException is thrown with message containing "Tool not found with id"

Scenario: Non-owner cannot delete another user's tool
   Given the user is authenticated as a researcher with username "tool.creator"
   And a tool named "brain-segmentation" already exists
   Given the user is authenticated as a researcher with username "other.user"
   When the user deletes that tool
   Then an AccessDeniedException is thrown

  Scenario: Admin can delete any tool
    Given a tool named "brain-segmentation" already exists
    And the user is authenticated as an admin
    When the user deletes that tool
    Then the tool is removed from the database
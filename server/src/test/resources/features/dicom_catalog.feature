Feature: DICOM image catalog management
  Authenticated users can list and delete their own DICOM images.
  Delete enforces ownership — another user's image returns a 404.

  Background:
    Given a catalog user exists with username "catalog.user"

  Scenario: A user with no uploaded images sees an empty catalog
    When the catalog user lists their images
    Then the image list is empty

  Scenario: A user sees only their own uploaded images
    Given the catalog user has uploaded 2 DICOM images
    When the catalog user lists their images
    Then the image list contains 2 images

  Scenario: Successfully delete an owned image
    Given the catalog user has uploaded 1 DICOM image
    When the catalog user deletes that image
    Then the image is removed from the database

  Scenario: Deleting another user's image throws ResourceNotFoundException
    Given another user owns a DICOM image
    When the catalog user attempts to delete that image
    Then a ResourceNotFoundException is thrown for the image

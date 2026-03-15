package com.green.imagecore.bdd;

import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.User;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.DicomImageRepository;
import com.green.imagecore.repositories.UserRepository;
import com.green.imagecore.service.DicomCatalogService;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Step definitions for DICOM catalog management.
 * Tests run against the service layer with a real PostgreSQL (Testcontainers)
 * and a mocked S3Client (defined in CucumberSpringConfiguration).
 */
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class DicomCatalogSteps {

    @Autowired
    private DicomCatalogService dicomCatalogService;

    @Autowired
    private DicomImageRepository dicomImageRepository;

    @Autowired
    private UserRepository userRepository;

    private User catalogUser;
    private User otherUser;
    private List<DicomImage> retrievedImages;
    private DicomImage selectedImage;
    private Exception thrownException;

    @After
    public void cleanUp() {
        dicomImageRepository.deleteAll();
        if (catalogUser != null) {
            userRepository.deleteById(catalogUser.getId());
            catalogUser = null;
        }
        if (otherUser != null) {
            userRepository.deleteById(otherUser.getId());
            otherUser = null;
        }
    }


    // GIVEN

    @Given("a catalog user exists with username {string}")
    public void a_catalog_user_exists_with_username(String username) {
        catalogUser = userRepository.findByUsername(username).orElseGet(() -> {
            User u = new User();
            u.setUsername(username);
            u.setEmail(username + "@test.com");
            u.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            return userRepository.save(u);
        });
    }

    @Given("the catalog user has uploaded {int} DICOM image(s)")
    public void the_catalog_user_has_uploaded_n_images(int count) {
        for (int i = 0; i < count; i++) {
            DicomImage img = new DicomImage();
            img.setUser(catalogUser);
            img.setFilename("scan" + i + ".dcm");
            img.setFileSize(1024L);
            img.setS3Key("dicom/" + catalogUser.getId() + "/" + UUID.randomUUID() + ".dcm");
            selectedImage = dicomImageRepository.save(img);
        }
        // selectedImage holds the last one — used by delete steps
    }

    @Given("another user owns a DICOM image")
    public void another_user_owns_a_dicom_image() {
        otherUser = userRepository.findByUsername("other.catalog.user").orElseGet(() -> {
            User u = new User();
            u.setUsername("other.catalog.user");
            u.setEmail("other.catalog@test.com");
            u.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            return userRepository.save(u);
        });

        DicomImage img = new DicomImage();
        img.setUser(otherUser);
        img.setFilename("other.dcm");
        img.setFileSize(512L);
        img.setS3Key("dicom/" + otherUser.getId() + "/" + UUID.randomUUID() + ".dcm");
        selectedImage = dicomImageRepository.save(img);
    }


    // WHEN

    @When("the catalog user lists their images")
    public void the_catalog_user_lists_their_images() {
        retrievedImages = dicomCatalogService.findAllForUser(catalogUser.getId());
    }

    @When("the catalog user deletes that image")
    public void the_catalog_user_deletes_that_image() {
        // S3Client is already mocked in CucumberSpringConfiguration — returns default response
        try {
            dicomCatalogService.delete(selectedImage.getId(), catalogUser.getId());
            thrownException = null;
        } catch (Exception e) {
            thrownException = e;
        }
    }

    @When("the catalog user attempts to delete that image")
    public void the_catalog_user_attempts_to_delete_that_image() {
        try {
            dicomCatalogService.delete(selectedImage.getId(), catalogUser.getId());
            thrownException = null;
        } catch (Exception e) {
            thrownException = e;
        }
    }


    // THEN

    @Then("the image list is empty")
    public void the_image_list_is_empty() {
        assertNotNull(retrievedImages);
        assertTrue(retrievedImages.isEmpty());
    }

    @Then("the image list contains {int} images")
    public void the_image_list_contains_n_images(int expected) {
        assertNotNull(retrievedImages);
        assertEquals(expected, retrievedImages.size());
    }

    @Then("the image is removed from the database")
    public void the_image_is_removed_from_the_database() {
        assertNull(thrownException, "No exception should have been thrown");
        assertFalse(dicomImageRepository.existsById(selectedImage.getId()),
                "Image should no longer exist in the repository");
    }

    @Then("a ResourceNotFoundException is thrown for the image")
    public void a_resource_not_found_exception_is_thrown_for_the_image() {
        assertNotNull(thrownException, "An exception should have been thrown");
        assertInstanceOf(ResourceNotFoundException.class, thrownException);
    }
}

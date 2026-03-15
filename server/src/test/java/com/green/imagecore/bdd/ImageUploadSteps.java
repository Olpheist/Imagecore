package com.green.imagecore.bdd;

import com.green.imagecore.entities.User;
import com.green.imagecore.repositories.UserRepository;
import com.green.imagecore.service.JwtService;
import com.green.imagecore.service.UserService;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ImageUploadSteps {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private S3Client s3Client;

    private MockMvc mockMvc;
    private String jwtToken;
    private ResultActions result;

    /**
     * The upload service looks up the user by ID from the JWT uid claim.
     * We create a dedicated test user here so the DB lookup succeeds for
     * scenarios where the role permits the upload.
     */
    private User uploadTestUser;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(springSecurity())
                .build();
        // Reset mock state between scenarios
        Mockito.reset(s3Client);

        try {
            uploadTestUser = userService.register(
                    "upload_bdd@example.com", "upload_bdd_user", "Password123!");
        } catch (IllegalArgumentException e) {
            // User persisted from a previous run — just retrieve it
            uploadTestUser = userRepository.findByEmail("upload_bdd@example.com").orElseThrow();
        }
    }

    @After
    public void cleanUp() {
        if (uploadTestUser != null) {
            userRepository.deleteById(uploadTestUser.getId());
            uploadTestUser = null;
        }
    }

    @Given("I am authenticated as a user with role {string}")
    public void i_am_authenticated_as_user_with_role(String role) {
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                "upload_bdd_user",
                "Password123!",
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        // Use the real user's DB id so the upload service can find the user
        this.jwtToken = jwtService.generateToken(userDetails, uploadTestUser.getId().toString());
    }

    @Given("I am not authenticated for upload")
    public void i_am_not_authenticated_for_upload() {
        this.jwtToken = null;
    }

    @When("I upload a valid DICOM file")
    public void i_upload_a_valid_dicom_file() throws Exception {
        performUpload(validDicomFile("scan.dcm"));
    }

    @When("I upload a non-DICOM file")
    public void i_upload_a_non_dicom_file() throws Exception {
        performUpload(new MockMultipartFile(
                "file", "report.txt", "text/plain", "not a dicom file".getBytes()
        ));
    }

    @When("I upload an empty file")
    public void i_upload_an_empty_file() throws Exception {
        performUpload(new MockMultipartFile(
                "file", "empty.dcm", "application/dicom", new byte[0]
        ));
    }

    @Then("the upload response status is {int}")
    public void the_upload_response_status_is(int expectedStatus) throws Exception {
        result.andExpect(status().is(expectedStatus));
    }

    @Then("the response body contains a DICOM image record")
    public void the_response_body_contains_a_dicom_image_record() throws Exception {
        result.andExpect(jsonPath("$.id").isNotEmpty())
              .andExpect(jsonPath("$.filename").isNotEmpty());
    }

    // helper: sends a mock multipart upload request, optionally adding a JWT
    // auth header, and saves the response for test verification
    private void performUpload(MockMultipartFile file) throws Exception {
        MockMultipartHttpServletRequestBuilder builder =
                multipart("/api/images/upload").file(file);
        if (jwtToken != null) {
            builder.header("Authorization", "Bearer " + jwtToken);
        }
        this.result = mockMvc.perform(builder);
    }

    /**
     * Builds a minimal valid DICOM Part 10 file in memory.
     * Structure: 128-byte preamble (zeroes) + "DICM" magic bytes + padding.
     */
    private MockMultipartFile validDicomFile(String filename) {
        byte[] content = new byte[200];
        content[128] = 'D';
        content[129] = 'I';
        content[130] = 'C';
        content[131] = 'M';
        return new MockMultipartFile("file", filename, "application/dicom", content);
    }
}

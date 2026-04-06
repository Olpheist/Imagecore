package com.green.imagecore.bdd;

import com.green.imagecore.repositories.UserRepository;
import com.green.imagecore.service.JwtService;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.medicalimaging.MedicalImagingClient;
import software.amazon.awssdk.services.medicalimaging.model.StartDicomImportJobRequest;
import software.amazon.awssdk.services.medicalimaging.model.StartDicomImportJobResponse;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class ImageUploadSteps {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private MedicalImagingClient medicalImagingClient;

    private MockMvc mockMvc;
    private String jwtToken;
    private ResultActions result;
    private com.green.imagecore.entities.User testUser;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(springSecurity())
                .defaultRequest(multipart("/").with(csrf()))
                .build();

        Mockito.reset(s3Client, medicalImagingClient);

        // Stub HealthImaging so the upload endpoint doesn't fail when triggering the import job
        StartDicomImportJobResponse importJobResponse = Mockito.mock(StartDicomImportJobResponse.class);
        Mockito.when(importJobResponse.jobId()).thenReturn("test-job-id");
        Mockito.when(medicalImagingClient.startDICOMImportJob(any(StartDicomImportJobRequest.class))).thenReturn(importJobResponse);

        // Create a real user so DicomUploadService can resolve the owner from the DB
        testUser = userRepository.save(
                com.green.imagecore.entities.User.builder()
                        .email("upload-test@example.com")
                        .username("uploadtestuser")
                        .passwordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy")
                        .build()
        );
    }

    @After
    public void tearDown() {
        // Only delete the user created in @Before; ToolSteps.@After handles tool users.
        // dicom_images are cascade-deleted when the user is removed.
        if (testUser != null) {
            userRepository.delete(testUser);
            testUser = null;
        }
    }

    @Given("I am authenticated as a user with role {string}")
    public void i_am_authenticated_as_user_with_role(String role) {
        UserDetails userDetails = new User(
                testUser.getEmail(),
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        this.jwtToken = jwtService.generateToken(userDetails, String.valueOf(testUser.getId()));
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

    @Then("the upload response contains an image ID and import status")
    public void the_upload_response_contains_an_image_id_and_import_status() throws Exception {
        result.andExpect(jsonPath("$.id").isNumber())
              .andExpect(jsonPath("$.importStatus").isNotEmpty());
    }

    // helper: sends a mock multipart upload request, optionally adding a JWT auth header
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
    static MockMultipartFile validDicomFile(String filename) {
        byte[] content = new byte[200];
        content[128] = 'D';
        content[129] = 'I';
        content[130] = 'C';
        content[131] = 'M';
        return new MockMultipartFile("file", filename, "application/dicom", content);
    }
}

package com.green.imagecore.bdd;

import com.green.imagecore.repositories.DicomImageRepository;
import com.green.imagecore.repositories.UserRepository;
import com.green.imagecore.service.JwtService;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.medicalimaging.MedicalImagingClient;
import software.amazon.awssdk.services.medicalimaging.model.DICOMImportJobProperties;
import software.amazon.awssdk.services.medicalimaging.model.GetDicomImportJobRequest;
import software.amazon.awssdk.services.medicalimaging.model.GetDicomImportJobResponse;
import software.amazon.awssdk.services.medicalimaging.model.GetImageSetMetadataRequest;
import software.amazon.awssdk.services.medicalimaging.model.JobStatus;
import software.amazon.awssdk.services.medicalimaging.model.StartDicomImportJobRequest;
import software.amazon.awssdk.services.medicalimaging.model.StartDicomImportJobResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class HealthImagingSteps {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MedicalImagingClient medicalImagingClient;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DicomImageRepository dicomImageRepository;

    private MockMvc mockMvc;
    private String jwtToken;
    private ResultActions result;
    private Long uploadedImageId;
    private com.green.imagecore.entities.User clinician;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(springSecurity())
                .build();

        Mockito.reset(s3Client, medicalImagingClient);

        // Stub startDICOMImportJob so the upload step succeeds without a real HealthImaging datastore
        StartDicomImportJobResponse importJobResponse = Mockito.mock(StartDicomImportJobResponse.class);
        Mockito.when(importJobResponse.jobId()).thenReturn("test-job-id");
        Mockito.when(medicalImagingClient.startDICOMImportJob(any(StartDicomImportJobRequest.class))).thenReturn(importJobResponse);

        // Create a persistent test user that DicomUploadService can resolve by ID from the JWT
        clinician = userRepository.save(
                com.green.imagecore.entities.User.builder()
                        .email("clinician@example.com")
                        .username("clinicianuser")
                        .passwordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy")
                        .build()
        );
    }

    @After
    public void tearDown() {
        // Only delete the clinician created in @Before; ToolSteps.@After handles tool users.
        // dicom_images are cascade-deleted when the user is removed.
        if (clinician != null) {
            userRepository.delete(clinician);
            clinician = null;
        }
    }


    // GIVEN

    @Given("I am a CLINICIAN with an uploaded DICOM file")
    public void i_am_a_clinician_with_an_uploaded_dicom_file() throws Exception {
        jwtToken = generateClinicianToken();
        uploadedImageId = performUploadAndExtractId();
    }

    @Given("I am a CLINICIAN with no uploaded images")
    public void i_am_a_clinician_with_no_uploaded_images() {
        jwtToken = generateClinicianToken();
        uploadedImageId = null;
    }

    @Given("I am a PATIENT")
    public void i_am_a_patient() {
        // PATIENT role is not authorised to list or check images (CLINICIAN-only endpoints)
        UserDetails userDetails = new User(
                clinician.getEmail(),
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))
        );
        jwtToken = jwtService.generateToken(userDetails, String.valueOf(clinician.getId()));
    }

    /**
     * Stubs getDICOMImportJob to return the given status string.
     * Runs after @Before so it overrides any default stubbing.
     * Supports: IN_PROGRESS, COMPLETED, FAILED, SUBMITTED.
     */
    @Given("HealthImaging reports the import job as {string}")
    public void health_imaging_reports_import_job_as(String statusStr) {
        JobStatus jobStatus = JobStatus.fromValue(statusStr);

        DICOMImportJobProperties props = Mockito.mock(DICOMImportJobProperties.class);
        Mockito.when(props.jobStatus()).thenReturn(jobStatus);
        Mockito.when(props.outputS3Uri()).thenReturn("s3://test-bucket/health-imaging-output/1/test/");

        GetDicomImportJobResponse statusResponse = Mockito.mock(GetDicomImportJobResponse.class);
        Mockito.when(statusResponse.jobProperties()).thenReturn(props);
        Mockito.when(medicalImagingClient.getDICOMImportJob(any(GetDicomImportJobRequest.class))).thenReturn(statusResponse);

        if (jobStatus == JobStatus.COMPLETED) {
            // Stub S3 manifest so extractImageSetId can read the imageSetId.
            // Without this, imageSetId is null and syncImportStatus marks the record FAILED.
            @SuppressWarnings("unchecked")
            ResponseBytes<GetObjectResponse> manifestBytes = Mockito.mock(ResponseBytes.class);
            Mockito.when(manifestBytes.asByteArray()).thenReturn(
                    "{\"imageSetsSummary\":[{\"imageSetId\":\"test-image-set-id\"}]}".getBytes());
            Mockito.when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(manifestBytes);

            // populateMetadata runs after COMPLETED — stub getImageSetMetadata to avoid NPE.
            Mockito.when(medicalImagingClient.getImageSetMetadata(any(GetImageSetMetadataRequest.class)))
                    .thenThrow(new RuntimeException("metadata not available in test"));
        }
    }


    // WHEN

    @When("I request my image list")
    public void i_request_my_image_list() throws Exception {
        var builder = get("/api/images");
        if (jwtToken != null) {
            builder.header("Authorization", "Bearer " + jwtToken);
        }
        result = mockMvc.perform(builder);
    }

    @When("I check the import status of my image")
    public void i_check_the_import_status_of_my_image() throws Exception {
        result = mockMvc.perform(
                get("/api/images/" + uploadedImageId + "/status")
                        .header("Authorization", "Bearer " + jwtToken)
        );
    }

    @When("I check the import status of image with ID {long}")
    public void i_check_the_import_status_of_image_with_id(Long id) throws Exception {
        result = mockMvc.perform(
                get("/api/images/" + id + "/status")
                        .header("Authorization", "Bearer " + jwtToken)
        );
    }


    // THEN

    @Then("the image list response status is {int}")
    public void the_image_list_response_status_is(int expectedStatus) throws Exception {
        result.andExpect(status().is(expectedStatus));
    }

    @And("the image list contains {int} image with status {string}")
    public void the_image_list_contains_image_with_status(int count, String importStatus) throws Exception {
        result.andExpect(jsonPath("$.length()").value(count))
              .andExpect(jsonPath("$[0].importStatus").value(importStatus));
    }

    @And("the image list response is empty")
    public void the_image_list_response_is_empty() throws Exception {
        result.andExpect(jsonPath("$.length()").value(0));
    }

    @Then("the status response is {int}")
    public void the_status_response_is(int expectedStatus) throws Exception {
        result.andExpect(status().is(expectedStatus));
    }

    @And("the import status in the response is {string}")
    public void the_import_status_in_the_response_is(String expectedStatus) throws Exception {
        result.andExpect(jsonPath("$.importStatus").value(expectedStatus));
    }


    // Helpers

    private String generateClinicianToken() {
        UserDetails userDetails = new User(
                clinician.getEmail(),
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_CLINICIAN"))
        );
        return jwtService.generateToken(userDetails, String.valueOf(clinician.getId()));
    }

    /**
     * Uploads a minimal DICOM file via the HTTP endpoint and returns the image ID from the response.
     * Relies on the startDICOMImportJob stub set up in @Before.
     */
    private Long performUploadAndExtractId() throws Exception {
        var uploadResult = mockMvc.perform(
                multipart("/api/images/upload")
                        .file(ImageUploadSteps.validDicomFile("test.dcm"))
                        .header("Authorization", "Bearer " + jwtToken)
        ).andReturn();

        String idStr = com.jayway.jsonpath.JsonPath.read(
                uploadResult.getResponse().getContentAsString(), "$.id"
        ).toString();
        return Long.parseLong(idStr);
    }
}

package com.green.imagecore.bdd;

import com.green.imagecore.entities.*;
import com.green.imagecore.entities.subscription.SubscriptionTierCode;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.AnalysisJobRepository;
import com.green.imagecore.repositories.DicomImageRepository;
import com.green.imagecore.repositories.ToolRepository;
import com.green.imagecore.repositories.UserRepository;
import com.green.imagecore.repositories.subscription.SubscriptionTierRepository;
import com.green.imagecore.service.AnalysisJobService;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Step definitions for analysis job submission and report download.
 * Tests run against the service layer with a real PostgreSQL (Testcontainers)
 * and mocked AWS clients (ECS, S3, S3Presigner).
 */
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class AnalysisJobSteps {

    @Autowired private AnalysisJobService        analysisJobService;
    @Autowired private AnalysisJobRepository     analysisJobRepository;
    @Autowired private DicomImageRepository      dicomImageRepository;
    @Autowired private ToolRepository            toolRepository;
    @Autowired private UserRepository            userRepository;
    @Autowired private SubscriptionTierRepository subscriptionTierRepository;
    @Autowired private EcsClient                 ecsClient;
    @Autowired private S3Client              s3Client;
    @Autowired private S3Presigner           s3Presigner;

    private User       jobUser;
    private User       otherUser;
    private DicomImage importedImage;
    private DicomImage unimportedImage;
    private DicomImage otherUserImage;
    private Tool       n4Tool;
    private AnalysisJob submittedJob;
    private Exception  thrownException;
    private URI        reportUri;

    @Before
    public void resetMocks() {
        Mockito.reset(ecsClient, s3Client, s3Presigner);

        // default ECS response: task submitted successfully
        Task task = Task.builder().taskArn("arn:aws:ecs:us-east-1:000000000000:task/cluster/test-task").build();
        RunTaskResponse success = RunTaskResponse.builder().tasks(task).failures(List.of()).build();
        when(ecsClient.runTask(any(RunTaskRequest.class))).thenReturn(success);
    }

    @After
    public void cleanUp() {
        analysisJobRepository.deleteAll();
        dicomImageRepository.deleteAll();
        toolRepository.deleteAll();
        if (jobUser   != null) userRepository.deleteById(jobUser.getId());
        if (otherUser != null) userRepository.deleteById(otherUser.getId());
        jobUser = otherUser = null;
        importedImage = unimportedImage = otherUserImage = null;
        n4Tool = null;
        submittedJob = null;
        thrownException = null;
        reportUri = null;
    }


    // GIVEN

    @Given("a job user exists with username {string}")
    public void a_job_user_exists(String username) {
        jobUser = userRepository.findByUsername(username).orElseGet(() -> {
            User u = new User();
            u.setUsername(username);
            u.setEmail(username + "@test.com");
            u.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            return userRepository.save(u);
        });
    }

    @Given("the job user has a DICOM image imported into HealthImaging")
    public void the_job_user_has_an_imported_image() {
        importedImage = new DicomImage();
        importedImage.setUser(jobUser);
        importedImage.setFilename("scan.dcm");
        importedImage.setFileSize(1024L);
        importedImage.setFileCount(1);
        importedImage.setS3Key("dicom/scan.dcm");
        importedImage.setImportStatus(ImportStatus.COMPLETED);
        importedImage.setImageSetId("img-set-abc123");
        importedImage = dicomImageRepository.save(importedImage);
    }

    @Given("a tool exists with name {string} and a task definition ARN")
    public void a_tool_exists_with_task_definition(String name) {
        // avoid duplicate if the tool already exists from a prior scenario
        n4Tool = toolRepository.findByName(name).orElseGet(() -> {
            Tool t = new Tool();
            t.setCreatedBy(jobUser);
            t.setName(name);
            t.setCategory("preprocessing");
            t.setDescription("N4 bias field correction");
            t.setTaskDefinitionArn("arn:aws:ecs:us-east-1:000000000000:task-definition/n4-bias-correction");
            t.setContainerName("app");
            t.setRequiredTier(subscriptionTierRepository.findByCode(SubscriptionTierCode.FREE)
                    .orElseThrow(() -> new IllegalStateException("FREE tier not seeded")));
            return toolRepository.save(t);
        });
    }

    @Given("the job user has an image that has not been imported into HealthImaging")
    public void the_job_user_has_an_unimported_image() {
        unimportedImage = new DicomImage();
        unimportedImage.setUser(jobUser);
        unimportedImage.setFilename("pending.dcm");
        unimportedImage.setFileSize(512L);
        unimportedImage.setFileCount(1);
        unimportedImage.setS3Key("dicom/pending.dcm");
        unimportedImage.setImportStatus(ImportStatus.PENDING);
        // imageSetId intentionally null (not yet imported)
        unimportedImage = dicomImageRepository.save(unimportedImage);
    }

    @Given("another user owns a separate DICOM image")
    public void another_user_owns_a_separate_image() {
        otherUser = userRepository.findByUsername("other.job.user").orElseGet(() -> {
            User u = new User();
            u.setUsername("other.job.user");
            u.setEmail("other.job@test.com");
            u.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            return userRepository.save(u);
        });
        otherUserImage = new DicomImage();
        otherUserImage.setUser(otherUser);
        otherUserImage.setFilename("other.dcm");
        otherUserImage.setFileSize(512L);
        otherUserImage.setFileCount(1);
        otherUserImage.setS3Key("dicom/other.dcm");
        otherUserImage.setImportStatus(ImportStatus.COMPLETED);
        otherUserImage.setImageSetId("other-img-set");
        otherUserImage = dicomImageRepository.save(otherUserImage);
    }


    // WHEN

    @When("the job user submits a job for their image using the n4 tool")
    public void the_job_user_submits_a_job() {
        try {
            submittedJob = analysisJobService.submit(importedImage.getId(), n4Tool.getToolId(), jobUser.getId());
            thrownException = null;
        } catch (Exception e) {
            thrownException = e;
        }
    }

    @When("the job user submits a job for that unimported image")
    public void the_job_user_submits_a_job_for_unimported_image() {
        try {
            submittedJob = analysisJobService.submit(unimportedImage.getId(), n4Tool.getToolId(), jobUser.getId());
            thrownException = null;
        } catch (Exception e) {
            thrownException = e;
        }
    }

    @When("the job user submits a job for the other user's image")
    public void the_job_user_submits_a_job_for_other_users_image() {
        try {
            submittedJob = analysisJobService.submit(otherUserImage.getId(), n4Tool.getToolId(), jobUser.getId());
            thrownException = null;
        } catch (Exception e) {
            thrownException = e;
        }
    }

    @And("the report has not been uploaded to S3 yet")
    public void the_report_has_not_been_uploaded() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Not found").build());
    }

    @And("the report has been uploaded to S3")
    public void the_report_has_been_uploaded() throws MalformedURLException {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

        String jobId = submittedJob != null ? String.valueOf(submittedJob.getId()) : "1";
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        doReturn(new URL("https://s3.amazonaws.com/test-bucket/results/" + jobId + "/report.pdf?sig=test"))
                .when(presigned).url();
        // service calls the Consumer<GetObjectPresignRequest.Builder> overload
        when(s3Presigner.presignGetObject(any(Consumer.class))).thenReturn(presigned);
    }


    // THEN

    @Then("a job record is created with status SUBMITTED")
    public void a_job_record_is_created_with_status_submitted() {
        assertThat(thrownException).isNull();
        assertThat(submittedJob).isNotNull();
        assertThat(submittedJob.getStatus()).isEqualTo(JobStatus.SUBMITTED);
        assertThat(analysisJobRepository.existsById(submittedJob.getId())).isTrue();
    }

    @Then("the job record stores an ECS task ARN")
    public void the_job_record_stores_an_ecs_task_arn() {
        assertThat(submittedJob.getEcsTaskArn()).isNotBlank();
    }

    @Then("an IllegalStateException is thrown mentioning HealthImaging")
    public void an_illegal_state_exception_is_thrown() {
        assertThat(thrownException).isInstanceOf(IllegalStateException.class);
        assertThat(thrownException.getMessage()).containsIgnoringCase("HealthImaging");
    }

    @Then("a ResourceNotFoundException is thrown because the image is not owned by the job user")
    public void a_resource_not_found_exception_is_thrown_because_image_not_owned() {
        assertThat(thrownException).isInstanceOf(ResourceNotFoundException.class);
    }

    @Then("requesting the report throws a ResourceNotFoundException mentioning {string}")
    public void requesting_the_report_throws_resource_not_found(String messageFragment) {
        try {
            analysisJobService.generateReportPresignedUrl(
                    importedImage.getId(), submittedJob.getId(), jobUser.getId()
            );
            thrownException = null;
        } catch (Exception e) {
            thrownException = e;
        }
        assertThat(thrownException).isInstanceOf(ResourceNotFoundException.class);
        assertThat(thrownException.getMessage()).contains(messageFragment);
    }

    @Then("requesting the report returns a presigned URI")
    public void requesting_the_report_returns_a_presigned_uri() {
        try {
            reportUri = analysisJobService.generateReportPresignedUrl(
                    importedImage.getId(), submittedJob.getId(), jobUser.getId()
            );
        } catch (Exception e) {
            thrownException = e;
        }
        assertThat(thrownException).isNull();
        assertThat(reportUri).isNotNull();
        assertThat(reportUri.toString()).contains("report.pdf");
    }
}

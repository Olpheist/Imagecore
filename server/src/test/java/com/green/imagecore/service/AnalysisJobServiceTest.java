package com.green.imagecore.service;

import com.green.imagecore.dto.AnalysisJobDto;
import com.green.imagecore.entities.AnalysisJob;
import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.JobStatus;
import com.green.imagecore.entities.Tool;
import com.green.imagecore.entities.User;
import com.green.imagecore.events.DicomImportSubmittedEvent;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.AnalysisJobRepository;
import com.green.imagecore.repositories.DicomImageRepository;
import com.green.imagecore.repositories.ToolRepository;
import com.green.imagecore.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalysisJobService Unit Tests")
class AnalysisJobServiceTest {

    @Mock private AnalysisJobRepository    analysisJobRepository;
    @Mock private DicomImageRepository     dicomImageRepository;
    @Mock private ToolRepository           toolRepository;
    @Mock private UserRepository           userRepository;
    @Mock private EcsClient                ecsClient;
    @Mock private S3Client                 s3Client;
    @Mock private S3Presigner              s3Presigner;
    @Mock private HealthImagingService     healthImagingService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AnalysisJobService analysisJobService;

    private DicomImage image;
    private Tool       tool;
    private User       user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(analysisJobService, "clusterArn",          "arn:aws:ecs:us-east-1:123:cluster/test");
        ReflectionTestUtils.setField(analysisJobService, "subnetIdsRaw",        "subnet-aaa");
        ReflectionTestUtils.setField(analysisJobService, "securityGroupIdsRaw", "sg-bbb");
        ReflectionTestUtils.setField(analysisJobService, "datastoreId",         "ds-001");
        ReflectionTestUtils.setField(analysisJobService, "s3BucketName",        "test-bucket");
        ReflectionTestUtils.setField(analysisJobService, "importRoleArn",       "arn:aws:iam::123:role/import-role");

        user = new User();
        user.setId(1L);
        user.setUsername("dr.smith");

        image = new DicomImage();
        image.setId(10L);
        image.setImageSetId("img-set-abc");
        image.setS3Key("dicom/1/scan-abc/");

        tool = new Tool();
        tool.setToolId(5L);
        tool.setName("brain-segmentation");
        tool.setTaskDefinitionArn("arn:aws:ecs:us-east-1:123:task-definition/brain-seg");
        tool.setContainerName("app");
    }


    // submit()

    @Nested
    @DisplayName("submit()")
    class Submit {

        @Test
        @DisplayName("returns a submitted job with ECS task ARN when all inputs are valid")
        void returnsSubmittedJobOnSuccess() {
            stubEcsSuccess("arn:aws:ecs:us-east-1:123:task/cluster/task-001");
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(image));
            when(toolRepository.findById(5L)).thenReturn(Optional.of(tool));
            when(userRepository.getReferenceById(1L)).thenReturn(user);
            when(analysisJobRepository.save(any())).thenAnswer(inv -> {
                AnalysisJob j = inv.getArgument(0);
                j.setId(99L);
                return j;
            });

            AnalysisJob result = analysisJobService.submit(10L, 5L, 1L);

            assertThat(result.getStatus()).isEqualTo(JobStatus.SUBMITTED);
            assertThat(result.getEcsTaskArn()).isEqualTo("arn:aws:ecs:us-east-1:123:task/cluster/task-001");
        }

        @Test
        @DisplayName("saves job with PENDING status before calling ECS")
        void savesPendingBeforeEcsCall() {
            stubEcsSuccess("arn:aws:ecs:us-east-1:123:task/cluster/task-001");
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(image));
            when(toolRepository.findById(5L)).thenReturn(Optional.of(tool));
            when(userRepository.getReferenceById(1L)).thenReturn(user);

            // Capture the status at each save() invocation before the object is mutated further
            List<JobStatus> capturedStatuses = new java.util.ArrayList<>();
            when(analysisJobRepository.save(any())).thenAnswer(inv -> {
                AnalysisJob j = inv.getArgument(0);
                capturedStatuses.add(j.getStatus());
                j.setId(99L);
                return j;
            });

            analysisJobService.submit(10L, 5L, 1L);

            assertThat(capturedStatuses.get(0)).isEqualTo(JobStatus.PENDING);
        }

        @Test
        @DisplayName("passes ORIGINAL_S3_BUCKET, ORIGINAL_S3_PREFIX, output, and HealthImaging env vars to ECS task")
        void passesCorrectEnvVarsToEcsTask() {
            stubEcsSuccess("arn:aws:ecs:us-east-1:123:task/cluster/task-001");
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(image));
            when(toolRepository.findById(5L)).thenReturn(Optional.of(tool));
            when(userRepository.getReferenceById(1L)).thenReturn(user);
            when(analysisJobRepository.save(any())).thenAnswer(inv -> {
                AnalysisJob j = inv.getArgument(0);
                j.setId(99L);
                return j;
            });

            analysisJobService.submit(10L, 5L, 1L);

            ArgumentCaptor<RunTaskRequest> captor = ArgumentCaptor.forClass(RunTaskRequest.class);
            verify(ecsClient).runTask(captor.capture());
            RunTaskRequest req = captor.getValue();

            List<KeyValuePair> envVars = req.overrides().containerOverrides().get(0).environment();
            assertThat(envVars).extracting(KeyValuePair::name)
                    .contains("ORIGINAL_S3_BUCKET", "ORIGINAL_S3_PREFIX",
                              "OUTPUT_S3_BUCKET", "OUTPUT_S3_PREFIX",
                              "DATASTORE_ID", "IMPORT_ROLE_ARN")
                    .doesNotContain("IMAGE_SET_ID");
            assertThat(envVars).filteredOn(e -> e.name().equals("ORIGINAL_S3_BUCKET"))
                    .extracting(KeyValuePair::value).containsOnly("test-bucket");
            assertThat(envVars).filteredOn(e -> e.name().equals("ORIGINAL_S3_PREFIX"))
                    .extracting(KeyValuePair::value).containsOnly("dicom/1/scan-abc/");
            assertThat(envVars).filteredOn(e -> e.name().equals("OUTPUT_S3_BUCKET"))
                    .extracting(KeyValuePair::value).containsOnly("test-bucket");
            assertThat(envVars).filteredOn(e -> e.name().equals("OUTPUT_S3_PREFIX"))
                    .extracting(KeyValuePair::value).containsOnly("results/99/");
            assertThat(envVars).filteredOn(e -> e.name().equals("DATASTORE_ID"))
                    .extracting(KeyValuePair::value).containsOnly("ds-001");
            assertThat(envVars).filteredOn(e -> e.name().equals("IMPORT_ROLE_ARN"))
                    .extracting(KeyValuePair::value).containsOnly("arn:aws:iam::123:role/import-role");
        }

        @Test
        @DisplayName("uses FARGATE launch type")
        void usesFargateLaunchType() {
            stubEcsSuccess("arn:aws:ecs:us-east-1:123:task/cluster/task-001");
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(image));
            when(toolRepository.findById(5L)).thenReturn(Optional.of(tool));
            when(userRepository.getReferenceById(1L)).thenReturn(user);
            when(analysisJobRepository.save(any())).thenAnswer(inv -> {
                AnalysisJob j = inv.getArgument(0);
                j.setId(99L);
                return j;
            });

            analysisJobService.submit(10L, 5L, 1L);

            ArgumentCaptor<RunTaskRequest> captor = ArgumentCaptor.forClass(RunTaskRequest.class);
            verify(ecsClient).runTask(captor.capture());
            assertThat(captor.getValue().launchType()).isEqualTo(LaunchType.FARGATE);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when image does not exist or is not owned by the user")
        void throwsWhenImageNotFound() {
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> analysisJobService.submit(10L, 5L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("10");

            verifyNoInteractions(ecsClient);
        }

        @Test
        @DisplayName("throws IllegalStateException when image has no imageSetId")
        void throwsWhenImageNotImportedYet() {
            image.setImageSetId(null);
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(image));

            assertThatThrownBy(() -> analysisJobService.submit(10L, 5L, 1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("10");

            verifyNoInteractions(ecsClient);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when tool does not exist")
        void throwsWhenToolNotFound() {
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(image));
            when(toolRepository.findById(5L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> analysisJobService.submit(10L, 5L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("5");

            verifyNoInteractions(ecsClient);
        }

        @Test
        @DisplayName("throws IllegalStateException when tool has no task definition ARN")
        void throwsWhenToolHasNoTaskDefinitionArn() {
            tool.setTaskDefinitionArn(null);
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(image));
            when(toolRepository.findById(5L)).thenReturn(Optional.of(tool));

            assertThatThrownBy(() -> analysisJobService.submit(10L, 5L, 1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("5");

            verifyNoInteractions(ecsClient);
        }

        @Test
        @DisplayName("saves job as FAILED and rethrows when ECS rejects the task")
        void savesFailedJobAndRethrowsWhenEcsRejects() {
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(image));
            when(toolRepository.findById(5L)).thenReturn(Optional.of(tool));
            when(userRepository.getReferenceById(1L)).thenReturn(user);
            when(analysisJobRepository.save(any())).thenAnswer(inv -> {
                AnalysisJob j = inv.getArgument(0);
                j.setId(99L);
                return j;
            });
            RunTaskResponse failureResponse = RunTaskResponse.builder()
                    .failures(Failure.builder().reason("RESOURCE:CPU").build())
                    .tasks(List.of())
                    .build();
            when(ecsClient.runTask(any(RunTaskRequest.class))).thenReturn(failureResponse);

            assertThatThrownBy(() -> analysisJobService.submit(10L, 5L, 1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ECS");

            ArgumentCaptor<AnalysisJob> captor = ArgumentCaptor.forClass(AnalysisJob.class);
            verify(analysisJobRepository, atLeast(2)).save(captor.capture());
            assertThat(captor.getAllValues()).anyMatch(j -> j.getStatus() == JobStatus.FAILED);
        }
    }


    // generateReportPresignedUrl()

    @Nested
    @DisplayName("generateReportPresignedUrl()")
    class GenerateReportPresignedUrl {

        @Test
        @DisplayName("returns a URI when image, job, and S3 object all exist")
        void returnsPresignedUriOnSuccess() throws MalformedURLException {
            AnalysisJob job = buildJob(20L, image);
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(image));
            when(analysisJobRepository.findById(20L)).thenReturn(Optional.of(job));
            when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

            PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
            doReturn(new URL("https://test-bucket.s3.amazonaws.com/results/20/report.pdf?sig=abc"))
                    .when(presigned).url();
            when(s3Presigner.presignGetObject(any(Consumer.class))).thenReturn(presigned);

            URI result = analysisJobService.generateReportPresignedUrl(10L, 20L, 1L);

            assertThat(result.toString()).contains("results/20/report.pdf");
        }

        @Test
        @DisplayName("checks the correct S3 key: results/{jobId}/report.pdf")
        void checksCorrectS3Key() throws MalformedURLException {
            AnalysisJob job = buildJob(20L, image);
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(image));
            when(analysisJobRepository.findById(20L)).thenReturn(Optional.of(job));
            when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

            PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
            doReturn(new URL("https://test-bucket.s3.amazonaws.com/results/20/report.pdf?sig=abc"))
                    .when(presigned).url();
            when(s3Presigner.presignGetObject(any(Consumer.class))).thenReturn(presigned);

            analysisJobService.generateReportPresignedUrl(10L, 20L, 1L);

            ArgumentCaptor<HeadObjectRequest> captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
            verify(s3Client).headObject(captor.capture());
            assertThat(captor.getValue().key()).isEqualTo("results/20/report.pdf");
            assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the image is not owned by the user")
        void throwsWhenImageNotOwnedByUser() {
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> analysisJobService.generateReportPresignedUrl(10L, 20L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("10");

            verifyNoInteractions(s3Client, s3Presigner);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the job does not exist")
        void throwsWhenJobNotFound() {
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(image));
            when(analysisJobRepository.findById(20L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> analysisJobService.generateReportPresignedUrl(10L, 20L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("20");

            verifyNoInteractions(s3Client, s3Presigner);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the job belongs to a different image")
        void throwsWhenJobBelongsToDifferentImage() {
            DicomImage otherImage = new DicomImage();
            otherImage.setId(99L);
            AnalysisJob job = buildJob(20L, otherImage);

            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(image));
            when(analysisJobRepository.findById(20L)).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> analysisJobService.generateReportPresignedUrl(10L, 20L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("20");

            verifyNoInteractions(s3Client, s3Presigner);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the report PDF is not in S3 yet")
        void throwsWhenReportNotYetAvailable() {
            AnalysisJob job = buildJob(20L, image);
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(image));
            when(analysisJobRepository.findById(20L)).thenReturn(Optional.of(job));
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(NoSuchKeyException.builder().message("Not found").build());

            assertThatThrownBy(() -> analysisJobService.generateReportPresignedUrl(10L, 20L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("20");

            verifyNoInteractions(s3Presigner);
        }
    }


    // findLatestJobForImage()

    @Nested
    @DisplayName("findLatestJobForImage()")
    class FindLatestJobForImage {

        @Test
        @DisplayName("returns the most recent job when one exists for the image")
        void returnsMostRecentJobWhenPresent() {
            AnalysisJob job = buildJob(30L, image);
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(image));
            when(analysisJobRepository.findFirstByImageIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(job));

            Optional<AnalysisJobDto> result = analysisJobService.findLatestJobForImage(10L, 1L);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(30L);
        }

        @Test
        @DisplayName("returns empty when no jobs have been submitted for the image yet")
        void returnsEmptyWhenNoJobsExist() {
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(image));
            when(analysisJobRepository.findFirstByImageIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.empty());

            Optional<AnalysisJobDto> result = analysisJobService.findLatestJobForImage(10L, 1L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException without querying jobs when image is not owned by user")
        void throwsWhenImageNotOwnedByUser() {
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> analysisJobService.findLatestJobForImage(10L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("10");

            verifyNoInteractions(analysisJobRepository);
        }
    }


    // pollActiveJobs()

    @Nested
    @DisplayName("pollActiveJobs()")
    class PollActiveJobs {

        private AnalysisJob job;

        @BeforeEach
        void setUpJob() {
            image.setUser(user);
            image.setFilename("brain.dcm");
            image.setModality("MR");

            job = AnalysisJob.builder()
                    .id(42L)
                    .image(image)
                    .tool(tool)
                    .user(user)
                    .status(JobStatus.SUBMITTED)
                    .ecsTaskArn("arn:aws:ecs:us-east-1:123:task/cluster/task-xyz")
                    .updatedAt(Instant.now())
                    .build();
        }

        @Test
        @DisplayName("transitions job to RUNNING when ECS task is RUNNING")
        void transitionsJobToRunning() {
            when(analysisJobRepository.findByStatusIn(anyList())).thenReturn(List.of(job));
            stubDescribeTasks("RUNNING", null);
            when(analysisJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analysisJobService.pollActiveJobs();

            ArgumentCaptor<AnalysisJob> captor = ArgumentCaptor.forClass(AnalysisJob.class);
            verify(analysisJobRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.RUNNING);
            verifyNoInteractions(dicomImageRepository, eventPublisher);
        }

        @Test
        @DisplayName("saves corrected DicomImage, publishes DicomImportSubmittedEvent, and marks job COMPLETED on success")
        void savesNewDicomImageAndPublishesEventOnSuccess() {
            when(analysisJobRepository.findByStatusIn(anyList())).thenReturn(List.of(job));
            stubDescribeTasks("STOPPED", 0);
            stubOutputJson("{\"healthImagingImportJobId\":\"hi-job-999\"}");
            when(dicomImageRepository.save(any(DicomImage.class))).thenAnswer(inv -> {
                DicomImage d = inv.getArgument(0);
                d.setId(77L);
                return d;
            });
            when(analysisJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(healthImagingService.syncImportStatus(any())).thenAnswer(inv -> inv.getArgument(0));

            analysisJobService.pollActiveJobs();

            // correct DicomImage saved with right job ID and ownership
            ArgumentCaptor<DicomImage> dicomCaptor = ArgumentCaptor.forClass(DicomImage.class);
            verify(dicomImageRepository).save(dicomCaptor.capture());
            assertThat(dicomCaptor.getValue().getHealthImagingJobId()).isEqualTo("hi-job-999");
            assertThat(dicomCaptor.getValue().getUser()).isEqualTo(user);

            // DicomImportSubmittedEvent published with the saved image ID so the import listener polls to completion
            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue()).isInstanceOf(DicomImportSubmittedEvent.class);
            assertThat(((DicomImportSubmittedEvent) eventCaptor.getValue()).imageId()).isEqualTo(77L);

            // job marked COMPLETED
            ArgumentCaptor<AnalysisJob> jobCaptor = ArgumentCaptor.forClass(AnalysisJob.class);
            verify(analysisJobRepository, atLeastOnce()).save(jobCaptor.capture());
            assertThat(jobCaptor.getAllValues()).anyMatch(j -> j.getStatus() == JobStatus.COMPLETED);
        }

        @Test
        @DisplayName("N4 tool creates corrected image at results/{id}/dicom/ with N4 labels")
        void n4ToolUsesCorrectS3PathAndLabels() {
            when(analysisJobRepository.findByStatusIn(anyList())).thenReturn(List.of(job));
            stubDescribeTasks("STOPPED", 0);
            stubOutputJson("{\"healthImagingImportJobId\":\"hi-job-n4\"}");
            when(dicomImageRepository.save(any(DicomImage.class))).thenAnswer(inv -> {
                DicomImage d = inv.getArgument(0);
                d.setId(77L);
                return d;
            });
            when(analysisJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(healthImagingService.syncImportStatus(any())).thenAnswer(inv -> inv.getArgument(0));

            analysisJobService.pollActiveJobs();

            ArgumentCaptor<DicomImage> captor = ArgumentCaptor.forClass(DicomImage.class);
            verify(dicomImageRepository, times(1)).save(captor.capture());
            DicomImage saved = captor.getValue();
            assertThat(saved.getS3Key()).isEqualTo("results/42/dicom/");
            assertThat(saved.getFilename()).isEqualTo("N4 Corrected - brain.dcm");
            assertThat(saved.getSeriesDescription()).isEqualTo("N4 Bias Field Corrected");
            verify(eventPublisher, times(1)).publishEvent(any(DicomImportSubmittedEvent.class));
        }

        @Test
        @DisplayName("Otsu tool creates masked and binary mask images with correct paths and labels, publishes two events, marks job COMPLETED")
        void otsuToolCreatesTwoImagesWithCorrectLabels() {
            when(analysisJobRepository.findByStatusIn(anyList())).thenReturn(List.of(job));
            stubDescribeTasks("STOPPED", 0);
            stubOutputJson("{\"healthImagingImportJobId\":\"hi-masked-001\",\"healthImagingBinaryMaskImportJobId\":\"hi-binary-002\"}");
            int[] idSeq = {100};
            when(dicomImageRepository.save(any(DicomImage.class))).thenAnswer(inv -> {
                DicomImage d = inv.getArgument(0);
                d.setId((long) idSeq[0]++);
                return d;
            });
            when(analysisJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(healthImagingService.syncImportStatus(any())).thenAnswer(inv -> inv.getArgument(0));

            analysisJobService.pollActiveJobs();

            ArgumentCaptor<DicomImage> dicomCaptor = ArgumentCaptor.forClass(DicomImage.class);
            verify(dicomImageRepository, times(2)).save(dicomCaptor.capture());
            List<DicomImage> saved = dicomCaptor.getAllValues();

            DicomImage masked = saved.get(0);
            assertThat(masked.getS3Key()).isEqualTo("results/42/dicom-masked/");
            assertThat(masked.getFilename()).isEqualTo("Otsu Masked - brain.dcm");
            assertThat(masked.getSeriesDescription()).isEqualTo("Otsu Threshold Masked");
            assertThat(masked.getHealthImagingJobId()).isEqualTo("hi-masked-001");

            DicomImage binary = saved.get(1);
            assertThat(binary.getS3Key()).isEqualTo("results/42/dicom-binary/");
            assertThat(binary.getFilename()).isEqualTo("Otsu Binary Mask - brain.dcm");
            assertThat(binary.getSeriesDescription()).isEqualTo("Otsu Threshold Binary Mask");
            assertThat(binary.getHealthImagingJobId()).isEqualTo("hi-binary-002");

            verify(eventPublisher, times(2)).publishEvent(any(DicomImportSubmittedEvent.class));
            ArgumentCaptor<AnalysisJob> jobCaptor = ArgumentCaptor.forClass(AnalysisJob.class);
            verify(analysisJobRepository, atLeastOnce()).save(jobCaptor.capture());
            assertThat(jobCaptor.getAllValues()).anyMatch(j -> j.getStatus() == JobStatus.COMPLETED);
        }

        @Test
        @DisplayName("marks job FAILED when ECS task exits with non-zero exit code")
        void savesJobAsFailedOnNonZeroExitCode() {
            when(analysisJobRepository.findByStatusIn(anyList())).thenReturn(List.of(job));
            stubDescribeTasks("STOPPED", 1);
            when(analysisJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analysisJobService.pollActiveJobs();

            ArgumentCaptor<AnalysisJob> captor = ArgumentCaptor.forClass(AnalysisJob.class);
            verify(analysisJobRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.FAILED);
            verifyNoInteractions(dicomImageRepository, eventPublisher);
        }

        @Test
        @DisplayName("marks job FAILED when output.json is missing from S3")
        void savesJobAsFailedWhenOutputJsonMissing() {
            when(analysisJobRepository.findByStatusIn(anyList())).thenReturn(List.of(job));
            stubDescribeTasks("STOPPED", 0);
            when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                    .thenThrow(NoSuchKeyException.builder().message("Not found").build());
            when(analysisJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analysisJobService.pollActiveJobs();

            ArgumentCaptor<AnalysisJob> captor = ArgumentCaptor.forClass(AnalysisJob.class);
            verify(analysisJobRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.FAILED);
            verifyNoInteractions(dicomImageRepository, eventPublisher);
        }

        @Test
        @DisplayName("marks job FAILED when output.json is missing healthImagingImportJobId")
        void savesJobAsFailedWhenHealthImagingJobIdMissing() {
            when(analysisJobRepository.findByStatusIn(anyList())).thenReturn(List.of(job));
            stubDescribeTasks("STOPPED", 0);
            stubOutputJson("{\"someOtherField\":\"value\"}");
            when(analysisJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analysisJobService.pollActiveJobs();

            ArgumentCaptor<AnalysisJob> captor = ArgumentCaptor.forClass(AnalysisJob.class);
            verify(analysisJobRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.FAILED);
            verifyNoInteractions(dicomImageRepository, eventPublisher);
        }

        @Test
        @DisplayName("does nothing when ECS returns no task for the ARN")
        void doesNothingWhenEcsTaskNotFound() {
            when(analysisJobRepository.findByStatusIn(anyList())).thenReturn(List.of(job));
            when(ecsClient.describeTasks(any(DescribeTasksRequest.class)))
                    .thenReturn(DescribeTasksResponse.builder().tasks(List.of()).build());

            analysisJobService.pollActiveJobs();

            verify(analysisJobRepository, never()).save(any());
            verifyNoInteractions(dicomImageRepository, eventPublisher);
        }

        private void stubDescribeTasks(String lastStatus, Integer exitCode) {
            Container container = exitCode != null
                    ? Container.builder().name("app").exitCode(exitCode).build()
                    : Container.builder().name("app").build();
            Task task = Task.builder()
                    .taskArn(job.getEcsTaskArn())
                    .lastStatus(lastStatus)
                    .containers(container)
                    .build();
            when(ecsClient.describeTasks(any(DescribeTasksRequest.class)))
                    .thenReturn(DescribeTasksResponse.builder().tasks(task).build());
        }

        private void stubOutputJson(String json) {
            when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                    .thenReturn(ResponseBytes.fromByteArray(
                            GetObjectResponse.builder().build(),
                            json.getBytes(StandardCharsets.UTF_8)));
        }
    }


    // Helpers

    private void stubEcsSuccess(String taskArn) {
        Task task = Task.builder().taskArn(taskArn).build();
        RunTaskResponse response = RunTaskResponse.builder()
                .tasks(task)
                .failures(List.of())
                .build();
        when(ecsClient.runTask(any(RunTaskRequest.class))).thenReturn(response);
    }

    private AnalysisJob buildJob(Long id, DicomImage jobImage) {
        return AnalysisJob.builder()
                .id(id)
                .image(jobImage)
                .tool(tool)
                .user(user)
                .status(JobStatus.SUBMITTED)
                .updatedAt(Instant.now())
                .build();
    }
}

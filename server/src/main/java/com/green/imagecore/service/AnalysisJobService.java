package com.green.imagecore.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.imagecore.dto.AnalysisJobDto;
import com.green.imagecore.entities.AnalysisJob;
import com.green.imagecore.mapper.AnalysisJobMapper;
import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.ImportStatus;
import com.green.imagecore.entities.JobStatus;
import com.green.imagecore.entities.Tool;
import com.green.imagecore.events.AnalysisJobCompletedEvent;
import com.green.imagecore.events.DicomImportSubmittedEvent;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.AnalysisJobRepository;
import com.green.imagecore.repositories.DicomImageRepository;
import com.green.imagecore.repositories.ToolRepository;
import com.green.imagecore.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisJobService {

    private final AnalysisJobRepository analysisJobRepository;
    private final DicomImageRepository  dicomImageRepository;
    private final ToolRepository        toolRepository;
    private final UserRepository        userRepository;
    private final EcsClient             ecsClient;
    private final S3Client              s3Client;
    private final S3Presigner           s3Presigner;
    private final HealthImagingService  healthImagingService;
    private final ApplicationEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // which ECS cluster to run the tool task on
    @Value("${app.aws.ecs.cluster-arn}")
    private String clusterArn;

    // comma-separated list of subnet IDs the Fargate task will run in
    @Value("${app.aws.ecs.subnet-ids}")
    private String subnetIdsRaw;

    // comma-separated list of security group IDs to attach to the task
    @Value("${app.aws.ecs.security-group-ids}")
    private String securityGroupIdsRaw;

    // HealthImaging datastore ID passed to the tool for the corrected DICOM reimport
    @Value("${app.aws.health-imaging.datastore-id}")
    private String datastoreId;

    // S3 bucket the tool will write its output to before reimporting into HealthImaging
    @Value("${app.aws.s3.bucket-name}")
    private String s3BucketName;

    // IAM role ARN that HealthImaging assumes when reimporting the corrected output from S3
    @Value("${app.aws.health-imaging.import-role-arn}")
    private String importRoleArn;

    /**
     * Creates an analysis job record and dispatches it to ECS as a Fargate task.
     *
     * @param imageId the ID of the DICOM image set to process
     * @param toolId  the ID of the tool to run
     * @param userId  the ID of the authenticated user submitting the job
     * @return the persisted {@link AnalysisJob} after ECS dispatch
     * @throws ResourceNotFoundException if the image or tool does not exist, or the image is not owned by the user
     * @throws IllegalStateException     if the image has no imageSetId or the tool has no taskDefinitionArn
     */
    @Transactional
    public AnalysisJob submit(Long imageId, Long toolId, Long userId) {
        // make sure the image exists and belongs to the user making the request
        DicomImage image = dicomImageRepository.findByIdAndUserId(imageId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found with id: " + imageId));

        // can't run a tool on an image that hasn't been imported into HealthImaging yet
        if (image.getImageSetId() == null) {
            throw new IllegalStateException("Image " + imageId + " has not been imported into HealthImaging yet");
        }

        // make sure the tool actually exists in the database
        Tool tool = toolRepository.findById(toolId)
                .orElseThrow(() -> new ResourceNotFoundException("Tool not found with id: " + toolId));

        // if the tool doesn't have a task definition, we have no way to run it in ECS
        if (tool.getTaskDefinitionArn() == null) {
            throw new IllegalStateException("Tool " + toolId + " does not have an ECS task definition configured");
        }

        // create the job record with PENDING status before calling ECS so there is an ID to reference
        // this way we always have a DB row even if the ECS call fails
        AnalysisJob job = AnalysisJob.builder()
                .image(image)
                .tool(tool)
                .user(userRepository.getReferenceById(userId))
                .status(JobStatus.PENDING)
                .updatedAt(Instant.now())
                .build();
        job = analysisJobRepository.save(job);

        try {
            // actually call ECS to start the container, get back the task ARN if it worked
            String ecsTaskArn = runEcsTask(job, image, tool);
            job.setStatus(JobStatus.SUBMITTED);
            job.setEcsTaskArn(ecsTaskArn);
            log.info("Submitted analysis job {} for image {} using tool {}, ECS task: {}",
                    job.getId(), imageId, toolId, ecsTaskArn);
        } catch (Exception e) {
            // if ECS rejected the request, mark the job as failed and save before rethrowing
            job.setStatus(JobStatus.FAILED);
            log.error("Failed to submit ECS task for analysis job {}: {}", job.getId(), e.getMessage(), e);
            analysisJobRepository.save(job);
            throw new RuntimeException("Failed to dispatch analysis job to ECS: " + e.getMessage(), e);
        }

        job.setUpdatedAt(Instant.now());
        return analysisJobRepository.save(job);
    }

    /**
     * Generates a 15-minute presigned S3 URL for the PDF report produced by an analysis job.
     * The report key is derived from the job ID as {@code results/{jobId}/report.pdf},
     * matching the prefix the ECS task writes to. No DB column is needed.
     *
     * @param imageId the ID of the DICOM image the job belongs to
     * @param jobId   the ID of the analysis job
     * @param userId  the ID of the authenticated user (ownership check)
     * @return a presigned URI valid for 15 minutes
     * @throws ResourceNotFoundException if the image, job, or report object does not exist
     */
    @Transactional(readOnly = true)
    public Optional<AnalysisJobDto> findLatestJobForImage(Long imageId, Long userId) {
        dicomImageRepository.findByIdAndUserId(imageId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found with id: " + imageId));
        return analysisJobRepository.findFirstByImageIdOrderByCreatedAtDesc(imageId)
                .map(AnalysisJobMapper::toDto);
    }

    public URI generateReportPresignedUrl(Long imageId, Long jobId, Long userId) {
        dicomImageRepository.findByIdAndUserId(imageId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found with id: " + imageId));

        AnalysisJob job = analysisJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));

        if (!job.getImage().getId().equals(imageId)) {
            throw new ResourceNotFoundException("Job not found with id: " + jobId);
        }

        String reportKey = "results/" + jobId + "/report.pdf";

        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3BucketName).key(reportKey).build());
        } catch (NoSuchKeyException e) {
            throw new ResourceNotFoundException(
                    "Report not yet available for job " + jobId + ", the tool may still be running");
        }

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(req ->
                req.signatureDuration(Duration.ofMinutes(15))
                   .getObjectRequest(get -> get.bucket(s3BucketName).key(reportKey))
        );

        try {
            return presigned.url().toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Presigned URL from S3 is not a valid URI", e);
        }
    }

    private String runEcsTask(AnalysisJob job, DicomImage image, Tool tool) {
        // split the comma-separated subnet and security group strings into lists for the ECS API
        List<String> subnetIds = Arrays.stream(subnetIdsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        List<String> securityGroupIds = Arrays.stream(securityGroupIdsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        // fall back to "app" if the tool doesn't have a container name set
        String containerName = tool.getContainerName() != null ? tool.getContainerName() : "app";

        // build the RunTask request, passing source and output paths as environment variable overrides
        // so the tool container knows where to read the original DICOM and where to write output
        RunTaskRequest request = RunTaskRequest.builder()
                .cluster(clusterArn)
                .taskDefinition(tool.getTaskDefinitionArn())
                .launchType(LaunchType.FARGATE)
                .networkConfiguration(NetworkConfiguration.builder()
                        .awsvpcConfiguration(AwsVpcConfiguration.builder()
                                .subnets(subnetIds)
                                .securityGroups(securityGroupIds)
                                .assignPublicIp(AssignPublicIp.ENABLED)
                                .build())
                        .build())
                .overrides(TaskOverride.builder()
                        .containerOverrides(ContainerOverride.builder()
                                .name(containerName)
                                .environment(
                                        // original DICOM upload location in S3 (source for the tool)
                                        KeyValuePair.builder().name("ORIGINAL_S3_BUCKET").value(s3BucketName).build(),
                                        KeyValuePair.builder().name("ORIGINAL_S3_PREFIX").value(image.getS3Key()).build(),
                                        // output location (each job gets its own prefix so outputs don't overwrite each other)
                                        KeyValuePair.builder().name("OUTPUT_S3_BUCKET").value(s3BucketName).build(),
                                        KeyValuePair.builder().name("OUTPUT_S3_PREFIX").value("results/" + job.getId() + "/").build(),
                                        // HealthImaging datastore for reimporting the corrected series
                                        KeyValuePair.builder().name("DATASTORE_ID").value(datastoreId).build(),
                                        KeyValuePair.builder().name("IMPORT_ROLE_ARN").value(importRoleArn).build()
                                )
                                .build())
                        .build())
                .build();

        RunTaskResponse response = ecsClient.runTask(request);

        // ECS can return HTTP 200 but still fail, failures list will tell us if something went wrong
        if (!response.failures().isEmpty()) {
            Failure failure = response.failures().get(0);
            throw new RuntimeException("ECS RunTask rejected the request: " + failure.reason());
        }

        // return the ARN of the started task so we can store it and look it up in the ECS console later
        return response.tasks().get(0).taskArn();
    }

    /**
     * Polls ECS every 30 seconds for SUBMITTED and RUNNING analysis jobs.
     * On task completion, reads the output.json written by the tool to S3,
     * creates a new DicomImage catalog entry for the corrected image set,
     * and marks the analysis job as COMPLETED or FAILED.
     */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void pollActiveJobs() {
        List<AnalysisJob> activeJobs = analysisJobRepository.findByStatusIn(
                List.of(JobStatus.SUBMITTED, JobStatus.RUNNING));

        for (AnalysisJob job : activeJobs) {
            try {
                syncJobStatus(job);
            } catch (Exception e) {
                log.error("Error polling ECS status for analysis job {}: {}", job.getId(), e.getMessage(), e);
            }
        }
    }

    private void syncJobStatus(AnalysisJob job) throws IOException {
        DescribeTasksResponse response = ecsClient.describeTasks(
                DescribeTasksRequest.builder()
                        .cluster(clusterArn)
                        .tasks(job.getEcsTaskArn())
                        .build());

        if (response.tasks().isEmpty()) {
            log.warn("ECS task {} not found for analysis job {}", job.getEcsTaskArn(), job.getId());
            return;
        }

        Task task = response.tasks().get(0);
        String lastStatus = task.lastStatus();

        if ("RUNNING".equals(lastStatus) && job.getStatus() == JobStatus.SUBMITTED) {
            job.setStatus(JobStatus.RUNNING);
            job.setUpdatedAt(Instant.now());
            analysisJobRepository.save(job);
            return;
        }

        if (!"STOPPED".equals(lastStatus)) {
            return;
        }

        // task has stopped, check exit code of the tool container
        int exitCode = task.containers().stream()
                .filter(c -> c.exitCode() != null)
                .mapToInt(c -> c.exitCode())
                .findFirst()
                .orElse(-1);

        if (exitCode != 0) {
            log.warn("Analysis job {} ECS task stopped with exit code {}", job.getId(), exitCode);
            job.setStatus(JobStatus.FAILED);
            job.setUpdatedAt(Instant.now());
            analysisJobRepository.save(job);
            return;
        }

        // tool succeeded, read the output.json the pipeline wrote to S3
        String outputKey = "results/" + job.getId() + "/output.json";
        String outputJson;
        try {
            outputJson = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(s3BucketName).key(outputKey).build()
            ).asUtf8String();
        } catch (NoSuchKeyException e) {
            log.warn("Analysis job {} completed but output.json not found at {}", job.getId(), outputKey);
            job.setStatus(JobStatus.FAILED);
            job.setUpdatedAt(Instant.now());
            analysisJobRepository.save(job);
            return;
        }

        JsonNode outputNode = objectMapper.readTree(outputJson);
        String healthImagingImportJobId = outputNode.path("healthImagingImportJobId").asText(null);
        if (healthImagingImportJobId == null) {
            log.warn("Analysis job {} output.json missing healthImagingImportJobId", job.getId());
            job.setStatus(JobStatus.FAILED);
            job.setUpdatedAt(Instant.now());
            analysisJobRepository.save(job);
            return;
        }

        // Otsu writes a second job ID for the binary mask; presence of this field
        // distinguishes it from single-output tools like N4
        String binaryMaskJobId = outputNode.path("healthImagingBinaryMaskImportJobId").asText(null);
        boolean isOtsu = binaryMaskJobId != null;

        // Skip row creation if output rows already exist for this import job.
        // syncImportStatus can throw after rows are saved but before the job is marked COMPLETED,
        // so on the next poll this guard prevents a second set of duplicate rows from being created.
        if (dicomImageRepository.existsByHealthImagingJobId(healthImagingImportJobId)) {
            log.warn("Analysis job {} already has output rows, marking COMPLETED without re-creating", job.getId());
            job.setStatus(JobStatus.COMPLETED);
            job.setUpdatedAt(Instant.now());
            analysisJobRepository.save(job);
            return;
        }

        DicomImage original = job.getImage();

        // primary corrected image: masked intensity series for Otsu, corrected series for N4
        DicomImage corrected = DicomImage.builder()
                .user(original.getUser())
                .s3Key("results/" + job.getId() + (isOtsu ? "/dicom-masked/" : "/dicom/"))
                .filename((isOtsu ? "Otsu Masked - " : "N4 Corrected - ") + original.getFilename())
                .fileSize(0L)
                .healthImagingJobId(healthImagingImportJobId)
                .importStatus(ImportStatus.SUBMITTED)
                .modality(original.getModality())
                .bodyPart(original.getBodyPart())
                .patientId(original.getPatientId())
                .studyDate(original.getStudyDate())
                .physician(original.getPhysician())
                .studyInstanceUid(original.getStudyInstanceUid())
                .studyDescription(original.getStudyDescription())
                .seriesDescription(isOtsu ? "Otsu Threshold Masked" : "N4 Bias Field Corrected")
                .build();
        corrected = dicomImageRepository.save(corrected);
        eventPublisher.publishEvent(new DicomImportSubmittedEvent(corrected.getId()));
        healthImagingService.syncImportStatus(corrected);

        // Otsu also produces a binary mask series; catalog it as a second image set
        if (isOtsu) {
            DicomImage binaryMask = DicomImage.builder()
                    .user(original.getUser())
                    .s3Key("results/" + job.getId() + "/dicom-binary/")
                    .filename("Otsu Binary Mask - " + original.getFilename())
                    .fileSize(0L)
                    .healthImagingJobId(binaryMaskJobId)
                    .importStatus(ImportStatus.SUBMITTED)
                    .modality(original.getModality())
                    .bodyPart(original.getBodyPart())
                    .patientId(original.getPatientId())
                    .studyDate(original.getStudyDate())
                    .physician(original.getPhysician())
                    .studyInstanceUid(original.getStudyInstanceUid())
                    .studyDescription(original.getStudyDescription())
                    .seriesDescription("Otsu Threshold Binary Mask")
                    .build();
            binaryMask = dicomImageRepository.save(binaryMask);
            eventPublisher.publishEvent(new DicomImportSubmittedEvent(binaryMask.getId()));
            healthImagingService.syncImportStatus(binaryMask);
        }

        job.setStatus(JobStatus.COMPLETED);
        job.setUpdatedAt(Instant.now());
        analysisJobRepository.save(job);
        eventPublisher.publishEvent(new AnalysisJobCompletedEvent(job.getId(), corrected.getId()));
        log.info("Analysis job {} completed for HealthImaging job {}",
                job.getId(), healthImagingImportJobId);
    }
}

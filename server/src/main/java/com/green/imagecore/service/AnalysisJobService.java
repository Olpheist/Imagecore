package com.green.imagecore.service;

import com.green.imagecore.entities.AnalysisJob;
import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.JobStatus;
import com.green.imagecore.entities.Tool;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.AnalysisJobRepository;
import com.green.imagecore.repositories.DicomImageRepository;
import com.green.imagecore.repositories.ToolRepository;
import com.green.imagecore.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisJobService {

    private final AnalysisJobRepository analysisJobRepository;
    private final DicomImageRepository  dicomImageRepository;
    private final ToolRepository        toolRepository;
    private final UserRepository        userRepository;
    private final EcsClient             ecsClient;

    // which ECS cluster to run the tool task on
    @Value("${app.aws.ecs.cluster-arn}")
    private String clusterArn;

    // comma-separated list of subnet IDs the Fargate task will run in
    @Value("${app.aws.ecs.subnet-ids}")
    private String subnetIdsRaw;

    // comma-separated list of security group IDs to attach to the task
    @Value("${app.aws.ecs.security-group-ids}")
    private String securityGroupIdsRaw;

    // HealthImaging datastore the tool will read the image set from
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

        // build the RunTask request, passing the image set info as environment variable overrides
        // so the tool container knows what to process and where to write output
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
                                        KeyValuePair.builder().name("IMAGE_SET_ID").value(image.getImageSetId()).build(),
                                        KeyValuePair.builder().name("DATASTORE_ID").value(datastoreId).build(),
                                        KeyValuePair.builder().name("OUTPUT_S3_BUCKET").value(s3BucketName).build(),
                                        // each job gets its own S3 prefix so outputs don't overwrite each other
                                        KeyValuePair.builder().name("OUTPUT_S3_PREFIX").value("results/" + job.getId() + "/").build(),
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
}

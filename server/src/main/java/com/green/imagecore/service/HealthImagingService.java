package com.green.imagecore.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.ImportStatus;
import com.green.imagecore.repositories.DicomImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.medicalimaging.MedicalImagingClient;
import software.amazon.awssdk.services.medicalimaging.model.DICOMImportJobProperties;
import software.amazon.awssdk.services.medicalimaging.model.GetDicomImportJobRequest;
import software.amazon.awssdk.services.medicalimaging.model.GetDicomImportJobResponse;
import software.amazon.awssdk.services.medicalimaging.model.JobStatus;
import software.amazon.awssdk.services.medicalimaging.model.StartDicomImportJobRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.net.URI;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthImagingService {

    private final MedicalImagingClient medicalImagingClient;
    private final S3Client s3Client;
    private final DicomImageRepository dicomImageRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.aws.health-imaging.datastore-id}")
    private String datastoreId;

    @Value("${app.aws.health-imaging.import-role-arn}")
    private String importRoleArn;

    /**
     * Submits a DICOM import job to AWS HealthImaging for the given S3 input prefix.
     *
     * @param inputS3Uri  S3 URI of the folder containing the uploaded DICOM file, e.g. s3://bucket/dicom/1/uuid/
     * @param outputS3Uri S3 URI prefix where HealthImaging will write the import job manifest
     * @return the HealthImaging job ID
     */
    public String startImportJob(String inputS3Uri, String outputS3Uri) {
        StartDicomImportJobRequest request = StartDicomImportJobRequest.builder()
                .datastoreId(datastoreId)
                .dataAccessRoleArn(importRoleArn)
                .inputS3Uri(inputS3Uri)
                .outputS3Uri(outputS3Uri)
                .build();

        String jobId = medicalImagingClient.startDICOMImportJob(request).jobId();
        log.debug("Started HealthImaging import job {} for input {}", jobId, inputS3Uri);
        return jobId;
    }

    /**
     * Checks the current status of a HealthImaging import job and saves the result to the database.
     * If the job has finished successfully, the imageSetId is pulled from the output manifest and saved.
     * If the status is already COMPLETED or FAILED, the method skips the AWS call since there's nothing left to update.
     *
     * @param image the DicomImage whose import status should be refreshed
     * @return the updated DicomImage
     */
    public DicomImage syncImportStatus(DicomImage image) {
        if (image.getImportStatus() == ImportStatus.COMPLETED
                || image.getImportStatus() == ImportStatus.FAILED) {
            return image;
        }

        GetDicomImportJobResponse response = medicalImagingClient.getDICOMImportJob(
                GetDicomImportJobRequest.builder()
                        .datastoreId(datastoreId)
                        .jobId(image.getHealthImagingJobId())
                        .build()
        );

        DICOMImportJobProperties props = response.jobProperties();
        JobStatus jobStatus = props.jobStatus();
        log.debug("HealthImaging job {} status: {}", image.getHealthImagingJobId(), jobStatus);

        switch (jobStatus) {
            case COMPLETED -> {
                String imageSetId = extractImageSetId(props.outputS3Uri());
                image.setImageSetId(imageSetId);
                image.setImportStatus(ImportStatus.COMPLETED);
            }
            case FAILED -> {
                log.warn("HealthImaging import job {} failed: {}",
                        image.getHealthImagingJobId(), props.message());
                image.setImportStatus(ImportStatus.FAILED);
            }
            case IN_PROGRESS -> image.setImportStatus(ImportStatus.IN_PROGRESS);
            case SUBMITTED   -> image.setImportStatus(ImportStatus.SUBMITTED);
            default          -> log.warn("Unrecognised HealthImaging job status: {}", jobStatus);
        }

        return dicomImageRepository.save(image);
    }

    /**
     * Reads the HealthImaging output manifest from S3 to extract the image set ID created by
     * a completed import job.
     * After a successful import, HealthImaging writes a manifest file to S3 at
     * {outputS3Uri}job-output-manifest.json. This method reads that file and pulls out
     * the imageSetId so it can be saved to the database.
     * The manifest looks like: {"imageSetsSummary": [{"imageSetId": "..."}]}
     * NOTE: Double-check the exact file path and JSON structure against the AWS HealthImaging docs
     * before relying on this in production.
     *
     * @param outputS3Uri the S3 URI prefix written to the StartDICOMImportJob request
     * @return the first image set ID found in the manifest, or null if it cannot be read
     */
    private String extractImageSetId(String outputS3Uri) {
        URI uri = URI.create(outputS3Uri);
        String bucket = uri.getHost();
        String keyPrefix = uri.getPath().substring(1); // strip leading '/'
        String manifestKey = keyPrefix + "job-output-manifest.json";

        try {
            byte[] bytes = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(manifestKey).build()
            ).asByteArray();

            JsonNode root = objectMapper.readTree(bytes);
            String imageSetId = root.path("imageSetsSummary").path(0).path("imageSetId").asText(null);
            log.debug("Extracted imageSetId {} from manifest at {}", imageSetId, manifestKey);
            return imageSetId;
        } catch (Exception e) {
            log.warn("Could not read imageSetId from HealthImaging output manifest at {}: {}",
                    outputS3Uri, e.getMessage());
            return null;
        }
    }
}

package com.green.imagecore.service;

import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.AnalysisJobRepository;
import com.green.imagecore.repositories.DicomImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.medicalimaging.MedicalImagingClient;
import software.amazon.awssdk.services.medicalimaging.model.ConflictException;
import software.amazon.awssdk.services.medicalimaging.model.DeleteImageSetRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;


import com.green.imagecore.dto.DicomSeriesGroupDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DicomCatalogService {

    private final DicomImageRepository dicomImageRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final MedicalImagingClient medicalImagingClient;
    private final S3Client s3Client;

    @Value("${app.aws.s3.bucket-name}")
    private String bucketName;

    @Value("${app.aws.health-imaging.datastore-id}")
    private String datastoreId;


    @Transactional(readOnly = true)
    public List<DicomImage> findAllForUser(Long userId) {
        return dicomImageRepository.findByUserIdOrderByUploadedAtDesc(userId);
    }

    /**
     * Returns one {@link DicomSeriesGroupDto} per HealthImaging imageSet owned by the user.
     * Images that share the same imageSetId are collapsed into a single row.
     * Images still pending import (no imageSetId yet) each appear as their own row.
     * Rows are ordered by the upload date of their earliest DB record (newest first).
     */
    @Transactional(readOnly = true)
    public List<DicomSeriesGroupDto> findSeriesGroupsForUser(Long userId) {
        List<DicomImage> images = dicomImageRepository.findByUserIdOrderByUploadedAtDesc(userId);

        // Preserve insertion order (already newest-first from the query)
        Map<String, List<DicomImage>> byKey = new LinkedHashMap<>();
        for (DicomImage img : images) {
            String key = img.getImageSetId() != null
                    ? img.getImageSetId()
                    : "__pending__" + img.getId();
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(img);
        }

        return byKey.entrySet().stream()
                .map(e -> toGroupDto(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private static final Map<String, Integer> STATUS_PRIORITY = Map.of(
            "COMPLETED",   4,
            "IN_PROGRESS", 3,
            "SUBMITTED",   2,
            "PENDING",     1,
            "FAILED",      0
    );

    private DicomSeriesGroupDto toGroupDto(String key, List<DicomImage> group) {
        DicomImage first = group.get(0);

        String displayName =
                first.getSeriesDescription() != null  ? first.getSeriesDescription()
              : first.getStudyDescription()  != null  ? first.getStudyDescription()
              : first.getImageSetId()        != null  ? first.getImageSetId()
              : first.getFilename();

        // frame_count is populated by populateMetadata() to the actual SOP instance count.
        // For records not yet re-populated it defaults to 1; the sync endpoint will correct it.
        int instanceCount = group.stream().mapToInt(img -> img.getFrameCount()).sum();

        String status = group.stream()
                .map(img -> img.getImportStatus().name())
                .max(Comparator.comparingInt(s -> STATUS_PRIORITY.getOrDefault(s, 0)))
                .orElse("PENDING");

        List<Long> imageIds = group.stream()
                .map(DicomImage::getId)
                .collect(Collectors.toList());

        return DicomSeriesGroupDto.builder()
                .key(key)
                .imageSetId(first.getImageSetId())
                .displayName(displayName)
                .modality(first.getModality())
                .bodyPart(first.getBodyPart())
                .studyDate(first.getStudyDate() != null ? first.getStudyDate().toString() : null)
                .instanceCount(instanceCount)
                .status(status)
                .seriesInstanceUid(first.getSeriesInstanceUid())
                .studyInstanceUid(first.getStudyInstanceUid())
                .imageIds(imageIds)
                .build();
    }

    @Transactional(readOnly = true)
    public DicomImage findByIdAndUser(Long imageId, Long userId) {
        return dicomImageRepository.findByIdAndUserId(imageId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found with id: " + imageId));
    }

    @Transactional(readOnly = true)
    public List<List<DicomImage>> findAllForUserGroupByImageSeries(Long userId) {
        return new ArrayList<>(dicomImageRepository.findByUserId(userId).stream()
                .collect(Collectors.groupingBy(img ->
                        img.getSeriesInstanceUid() != null ? img.getSeriesInstanceUid() : "UNKNOWN_SERIES"
                ))
                .values());
    }


    /**
     * Deletes a DICOM series — all S3 objects under the series prefix first, then the DB row.
     * If S3 deletion fails, the DB row is left intact (files remain recoverable).
     * <p>
     * Uses ListObjectsV2 + DeleteObjects (batch delete). Works for both new-style rows
     * (s3Key is a prefix like {@code dicom/42/uuid/}) and legacy rows (s3Key is a full
     * object path like {@code dicom/42/uuid/instance.dcm}) — a prefix query on the latter
     * matches exactly that one object.
     * <p>
     * Note: handles up to 1000 objects (one ListObjectsV2 page). DICOM series rarely exceed
     * this limit; pagination can be added if needed.
     */
    @Transactional
    public void delete(Long imageId, Long userId) {
        DicomImage image = findByIdAndUser(imageId, userId);

        // Remove any analysis jobs that used this image as their input. Without this,
        // the analysis_jobs.image_id FK would block the DB delete below.
        analysisJobRepository.deleteByImageId(imageId);

        // Delete from HealthImaging if the image set exists.
        // When multiple DB rows share the same imageSetId (e.g. individually uploaded slices
        // of the same series that AWS HealthImaging merged), the first delete removes the
        // imageSet and subsequent calls will receive a ResourceNotFoundException — swallow it.
        if (image.getImageSetId() != null) {
            try {
                DeleteImageSetRequest deleteImageSetRequest = DeleteImageSetRequest.builder()
                        .datastoreId(datastoreId)
                        .imageSetId(image.getImageSetId())
                        .build();
                medicalImagingClient.deleteImageSet(deleteImageSetRequest);
            } catch (software.amazon.awssdk.services.medicalimaging.model.ResourceNotFoundException e) {
                log.info("ImageSet {} already deleted, skipping HealthImaging removal for image {}", image.getImageSetId(), imageId);
            } catch (ConflictException e) {
                log.warn("ImageSet {} is not in a deletable state for image {}: {}", image.getImageSetId(), imageId, e.getMessage());
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The image set is still being processed by HealthImaging. Please try again in a moment.");
            } catch (SdkException e) {
                log.error("Unexpected HealthImaging error deleting imageSet {} for image {}: {}", image.getImageSetId(), imageId, e.getMessage(), e);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "HealthImaging is unavailable. Please try again later.");
            }
        }

        // Delete from S3 (all files under the series prefix)
        ListObjectsV2Response listResponse = s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .prefix(image.getS3Key())
                        .build()
        );

        if (listResponse != null && listResponse.contents() != null && !listResponse.contents().isEmpty()) {
            List<ObjectIdentifier> toDelete = listResponse.contents().stream()
                    .map(s3Obj -> ObjectIdentifier.builder().key(s3Obj.key()).build())
                    .collect(Collectors.toList());

            s3Client.deleteObjects(
                    DeleteObjectsRequest.builder()
                            .bucket(bucketName)
                            .delete(Delete.builder().objects(toDelete).build())
                            .build()
            );
            log.info("Deleted {} S3 objects under prefix {} for image {}", toDelete.size(), image.getS3Key(), imageId);
        }

        // Delete from database (only after external resources are cleaned up)
        dicomImageRepository.delete(image);
    }
}

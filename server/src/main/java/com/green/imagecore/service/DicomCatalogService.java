package com.green.imagecore.service;

import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.DicomImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.medicalimaging.model.DeleteImageSetRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.medicalimaging.MedicalImagingClient;


import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DicomCatalogService {

    private final DicomImageRepository dicomImageRepository;
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

    @Transactional(readOnly = true)
    public DicomImage findByIdAndUser(Long imageId, Long userId) {
        return dicomImageRepository.findByIdAndUserId(imageId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found with id: " + imageId));
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

        // Delete from HealthImaging if the image set exists
        if (image.getImageSetId() != null) {
            DeleteImageSetRequest deleteImageSetRequest = DeleteImageSetRequest.builder()
                    .datastoreId(datastoreId)
                    .imageSetId(image.getImageSetId())
                    .build();
            medicalImagingClient.deleteImageSet(deleteImageSetRequest);
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

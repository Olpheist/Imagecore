package com.green.imagecore.service;

import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.DicomImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DicomCatalogService {

    private final DicomImageRepository dicomImageRepository;
    private final S3Client s3Client;

    @Value("${app.aws.s3.bucket-name}")
    private String bucketName;

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
     * Deletes a DICOM image — S3 object first, then DB row.
     * If S3 deletion fails, the DB row is left intact (file remains recoverable).
     */
    @Transactional
    public void delete(Long imageId, Long userId) {
        DicomImage image = findByIdAndUser(imageId, userId);

        s3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(image.getS3Key())
                        .build()
        );

        dicomImageRepository.delete(image);
    }
}

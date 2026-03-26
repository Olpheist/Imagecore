package com.green.imagecore.service;

import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.ImportStatus;
import com.green.imagecore.entities.User;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.DicomImageRepository;
import com.green.imagecore.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DicomUploadService {

    // DICOM Part 10 files have a 128-byte preamble followed by the 4-byte "DICM" magic string
    private static final int DICOM_PREAMBLE_LENGTH = 128;
    private static final byte[] DICOM_MAGIC = {'D', 'I', 'C', 'M'};

    private final S3Client s3Client;
    private final DicomImageRepository dicomImageRepository;
    private final UserRepository userRepository;
    private final HealthImagingService healthImagingService;

    @Value("${app.aws.s3.bucket-name}")
    private String bucketName;

    /**
     * Validates the file, uploads it to S3, persists a DicomImage record, and starts a
     * HealthImaging import job. Each upload is stored in its own S3 folder so HealthImaging
     * can be given a dedicated prefix for the import.
     * S3 key:               dicom/{userId}/{uploadId}/instance.dcm
     * HealthImaging input:  s3://{bucket}/dicom/{userId}/{uploadId}/
     * HealthImaging output: s3://{bucket}/health-imaging-output/{userId}/{uploadId}/
     *
     * @param file   The multipart DICOM file from the client.
     * @param userId The authenticated user's database ID.
     * @return The persisted DicomImage record with the job ID and initial import status.
     */
    public DicomImage upload(MultipartFile file, Long userId) {
        validateDicom(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        String uploadId = UUID.randomUUID().toString();
        String s3Key = "dicom/" + userId + "/" + uploadId + "/instance.dcm";

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(s3Key)
                            .contentType("application/dicom")
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to read upload stream", e);
        }

        DicomImage image = dicomImageRepository.save(
                DicomImage.builder()
                        .user(user)
                        .s3Key(s3Key)
                        .filename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.dcm")
                        .fileSize(file.getSize())
                        .importStatus(ImportStatus.PENDING)
                        .build()
        );

        String inputS3Uri  = "s3://" + bucketName + "/dicom/" + userId + "/" + uploadId + "/";
        String outputS3Uri = "s3://" + bucketName + "/health-imaging-output/" + userId + "/" + uploadId + "/";

        String jobId = healthImagingService.startImportJob(inputS3Uri, outputS3Uri);
        image.setHealthImagingJobId(jobId);
        image.setImportStatus(ImportStatus.SUBMITTED);

        return dicomImageRepository.save(image);
    }

    /**
     * Uploads multiple DICOM files and persists a DB record for each.
     */
    public List<DicomImage> uploadBatch(List<MultipartFile> files, Long userId) {
        return files.stream()
                .map(file -> upload(file, userId))
                .collect(Collectors.toList());
    }

    /**
     * Verifies the DICOM Part 10 magic bytes at offset 128.
     * This prevents non-DICOM files from being stored in the medical image bucket.
     */
    private void validateDicom(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        byte[] header;
        try {
            header = file.getInputStream().readNBytes(DICOM_PREAMBLE_LENGTH + DICOM_MAGIC.length);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file for validation", e);
        }

        if (header.length < DICOM_PREAMBLE_LENGTH + DICOM_MAGIC.length) {
            throw new IllegalArgumentException("File is too small to be a valid DICOM file");
        }

        for (int i = 0; i < DICOM_MAGIC.length; i++) {
            if (header[DICOM_PREAMBLE_LENGTH + i] != DICOM_MAGIC[i]) {
                throw new IllegalArgumentException("File is not a valid DICOM file");
            }
        }
    }
}

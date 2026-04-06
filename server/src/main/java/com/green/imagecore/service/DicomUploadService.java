package com.green.imagecore.service;

import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.ImportStatus;
import com.green.imagecore.entities.User;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.DicomImageRepository;
import com.green.imagecore.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

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

    @Autowired
    @Qualifier("dicomS3UploadExecutor")
    private ExecutorService s3UploadExecutor;

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
                        .fileCount(1)
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
     * Uploads a batch of DICOM files as a single series. All files are placed under one shared
     * S3 prefix, then a single HealthImaging import job is started for that prefix.
     * <p>
     * S3 prefix:            dicom/{userId}/{batchId}/
     * HealthImaging input:  s3://{bucket}/dicom/{userId}/{batchId}/
     * HealthImaging output: s3://{bucket}/health-imaging-output/{userId}/{batchId}/
     * <p>
     * Note: if two files in the batch share the same original filename, the last write wins.
     * DICOM series files are typically uniquely named (SOP Instance UID), so this is not a
     * practical concern.
     *
     * @param files  The list of multipart DICOM files from the client.
     * @param userId The authenticated user's database ID.
     * @return A single DicomImage record representing the entire series.
     */
    public DicomImage uploadBatch(List<MultipartFile> files, Long userId) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one file must be provided");
        }

        // Phase 1: validate all files before touching S3
        for (MultipartFile file : files) {
            validateDicom(file);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        String batchId = UUID.randomUUID().toString();
        String prefix  = "dicom/" + userId + "/" + batchId + "/";

        // Phase 2: upload all files to the shared S3 prefix in parallel
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : UUID.randomUUID() + ".dcm";
            String s3Key = prefix + filename;
            futures.add(CompletableFuture.runAsync(() -> {
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
                    throw new CompletionException(new RuntimeException("S3 upload failed for " + s3Key, e));
                }
            }, s3UploadExecutor));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (CompletionException ex) {
            throw new RuntimeException("One or more S3 uploads failed for batch " + batchId, ex.getCause());
        }

        // Phase 3: build a display label for the series record
        String firstFilename = files.get(0).getOriginalFilename() != null
                ? files.get(0).getOriginalFilename() : "unknown.dcm";
        String label = files.size() == 1
                ? firstFilename
                : firstFilename + " + " + (files.size() - 1) + " more";

        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();

        DicomImage image = dicomImageRepository.save(
                DicomImage.builder()
                        .user(user)
                        .s3Key(prefix)
                        .filename(label)
                        .fileSize(totalSize)
                        .fileCount(files.size())
                        .importStatus(ImportStatus.PENDING)
                        .build()
        );

        // Phase 4: start ONE HealthImaging import job for the entire series prefix
        String inputS3Uri  = "s3://" + bucketName + "/" + prefix;
        String outputS3Uri = "s3://" + bucketName + "/health-imaging-output/" + userId + "/" + batchId + "/";

        String jobId = healthImagingService.startImportJob(inputS3Uri, outputS3Uri);
        image.setHealthImagingJobId(jobId);
        image.setImportStatus(ImportStatus.SUBMITTED);

        return dicomImageRepository.save(image);
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

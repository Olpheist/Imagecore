package com.green.imagecore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DicomUploadService {

    //DICOM Part 10 files have a 128-byte preamble followed by the 4-byte "DICM" magic string
    private static final int DICOM_PREAMBLE_LENGTH = 128;
    private static final byte[] DICOM_MAGIC = {'D', 'I', 'C', 'M'};

    private final S3Client s3Client;

    @Value("${app.aws.s3.bucket-name}")
    private String bucketName;

    /**
     * Validates that the file is a DICOM file, then uploads it to S3.
     *
     * @param file   The multipart file received from the client.
     * @param userId The authenticated user's database ID, used to namespace the S3 key.
     * @return The S3 object key of the stored file.
     */
    public String upload(MultipartFile file, String userId) {
        validateDicom(file);

        String key = "dicom/" + userId + "/" + UUID.randomUUID() + ".dcm";

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType("application/dicom")
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to read upload stream", e);
        }

        return key;
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

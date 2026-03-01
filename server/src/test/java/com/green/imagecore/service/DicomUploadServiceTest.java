package com.green.imagecore.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DicomUploadServiceTest {

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private DicomUploadService dicomUploadService;

    @BeforeEach
    void setUp() {
        // @Value fields are not injected by Mockito, so set them via reflection
        ReflectionTestUtils.setField(dicomUploadService, "bucketName", "test-bucket");
    }


    //happy paths
    @Test
    void upload_ReturnsKeyWithUserIdAndDcmExtension() {
        String key = dicomUploadService.upload(validDicomFile("scan.dcm"), "42");

        assertTrue(key.startsWith("dicom/42/"),
                "Key should be namespaced under dicom/{userId}/");
        assertTrue(key.endsWith(".dcm"),
                "Key should use the .dcm extension");
    }

    @Test
    void upload_CallsS3PutObjectWithCorrectBucketAndContentType() {
        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);

        dicomUploadService.upload(validDicomFile("scan.dcm"), "42");

        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest putRequest = requestCaptor.getValue();

        assertEquals("test-bucket", putRequest.bucket());
        assertEquals("application/dicom", putRequest.contentType());
    }

    @Test
    void upload_S3KeyContainsUserId() {
        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);

        dicomUploadService.upload(validDicomFile("scan.dcm"), "user-999");

        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertTrue(requestCaptor.getValue().key().contains("user-999"));
    }

    //sad paths
    @Test
    void upload_ThrowsException_WhenFileIsEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.dcm", "application/dicom", new byte[0]);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dicomUploadService.upload(emptyFile, "42"));

        assertEquals("File must not be empty", ex.getMessage());
    }

    @Test
    void upload_DoesNotCallS3_WhenFileIsEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.dcm", "application/dicom", new byte[0]);

        assertThrows(IllegalArgumentException.class,
                () -> dicomUploadService.upload(emptyFile, "42"));

        verifyNoInteractions(s3Client);
    }

    @Test
    void upload_ThrowsException_WhenFileTooSmallForDicomHeader() {
        MockMultipartFile tooSmall = new MockMultipartFile(
                "file", "tiny.dcm", "application/dicom", new byte[50]);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dicomUploadService.upload(tooSmall, "42"));

        assertEquals("File is too small to be a valid DICOM file", ex.getMessage());
        verifyNoInteractions(s3Client);
    }

    @Test
    void upload_ThrowsException_WhenMagicBytesAreMissing() {
        //create fake file filled with zeros, no "DICM" at byte 128 should fail validation
        MockMultipartFile notDicom = new MockMultipartFile(
                "file", "fake.dcm", "application/dicom", new byte[200]);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dicomUploadService.upload(notDicom, "42"));

        assertEquals("File is not a valid DICOM file", ex.getMessage());

        //make sure nothing was sent to S3 since file is invalid
        verifyNoInteractions(s3Client);
    }

    @Test
    void upload_ThrowsException_WhenMagicBytesArePartiallyCorrect() {
        //only part of "DICM" is present
        byte[] content = new byte[200];
        content[128] = 'D';
        content[129] = 'I';
        //leave last two bytes incorrect on purpose
        MockMultipartFile partialMagic = new MockMultipartFile(
                "file", "partial.dcm", "application/dicom", content);

        assertThrows(IllegalArgumentException.class,
                () -> dicomUploadService.upload(partialMagic, "42"));

        //make sure nothing was sent to S3 since file is invalid
        verifyNoInteractions(s3Client);
    }

    //helper dicom file
    private MockMultipartFile validDicomFile(String filename) {
        byte[] content = new byte[200];
        content[128] = 'D';
        content[129] = 'I';
        content[130] = 'C';
        content[131] = 'M';
        return new MockMultipartFile("file", filename, "application/dicom", content);
    }
}

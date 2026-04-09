package com.green.imagecore.service;

import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.ImportStatus;
import com.green.imagecore.entities.User;
import com.green.imagecore.repositories.DicomImageRepository;
import com.green.imagecore.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DicomUploadServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private DicomImageRepository dicomImageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HealthImagingService healthImagingService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DicomUploadService dicomUploadService;

    private User testUser;

    @BeforeEach
    void setUp() {
        // @Value fields are not injected by Mockito, so set via reflection
        ReflectionTestUtils.setField(dicomUploadService, "bucketName", "test-bucket");
        // Inject a real single-threaded executor for testing
        ReflectionTestUtils.setField(dicomUploadService, "s3UploadExecutor", Executors.newSingleThreadExecutor());

        testUser = User.builder()
                .id(42L).email("test@test.com").username("testuser").passwordHash("hash")
                .build();

        // Lenient: sad-path tests reject files before reaching these stubs
        lenient().when(userRepository.findById(42L)).thenReturn(Optional.of(testUser));
        lenient().when(healthImagingService.startImportJob(anyString(), anyString())).thenReturn("test-job-id");
        // Return the DicomImage passed in, assigning an ID on first save if needed
        lenient().when(dicomImageRepository.save(any(DicomImage.class))).thenAnswer(inv -> {
            DicomImage img = inv.getArgument(0);
            if (img.getId() == null) {
                img.setId(999L); // assign ID on first save
            }
            return img;
        });
    }


    // happy paths

    @Test
    void upload_ReturnsDicomImageWithS3KeyNamespacedToUser() {
        DicomImage result = dicomUploadService.upload(validDicomFile("scan.dcm"), 42L);

        assertNotNull(result);
        assertTrue(result.getS3Key().startsWith("dicom/42/"),
                "S3 key should be namespaced under dicom/{userId}/");
    }

    @Test
    void upload_ReturnsDicomImageWithCorrectFilenameAndSize() {
        DicomImage result = dicomUploadService.upload(validDicomFile("scan.dcm"), 42L);

        assertEquals("scan.dcm", result.getFilename());
        assertEquals(200L, result.getFileSize());
    }

    @Test
    void upload_ReturnsDicomImageLinkedToCorrectUser() {
        DicomImage result = dicomUploadService.upload(validDicomFile("scan.dcm"), 42L);

        assertNotNull(result.getUser());
        assertEquals(42L, result.getUser().getId());
    }

    @Test
    void upload_CallsS3PutObjectWithCorrectBucketAndContentType() {
        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);

        dicomUploadService.upload(validDicomFile("scan.dcm"), 42L);

        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest putRequest = requestCaptor.getValue();

        assertEquals("test-bucket", putRequest.bucket());
        assertEquals("application/dicom", putRequest.contentType());
    }

    @Test
    void upload_S3KeyContainsUserId() {
        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);

        dicomUploadService.upload(validDicomFile("scan.dcm"), 42L);

        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertTrue(requestCaptor.getValue().key().contains("42"));
    }

    @Test
    void upload_ReturnsImageWithInProgressStatus() {
        DicomImage result = dicomUploadService.upload(validDicomFile("scan.dcm"), 42L);

        // upload() calls startImportJob synchronously and then advances the status to IN_PROGRESS
        assertEquals(ImportStatus.IN_PROGRESS, result.getImportStatus());
        assertNotNull(result.getHealthImagingJobId());
    }

    @Test
    void upload_PersistsDicomImageToRepository() {
        dicomUploadService.upload(validDicomFile("scan.dcm"), 42L);

        verify(dicomImageRepository, times(2)).save(any(DicomImage.class));
    }

    @Test
    void uploadBatch_ReturnsSingleDicomImage() {
        List<org.springframework.web.multipart.MultipartFile> files = List.of(
                validDicomFile("scan1.dcm"),
                validDicomFile("scan2.dcm"),
                validDicomFile("scan3.dcm")
        );

        DicomImage result = dicomUploadService.uploadBatch(files, 42L);

        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals(3, result.getFileCount());
        assertEquals(600L, result.getFileSize()); // 3 files * 200 bytes each
        verify(s3Client, times(3)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(healthImagingService, times(1)).startImportJob(anyString(), anyString());
    }

    @Test
    void uploadBatch_EmptyList_ThrowsIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dicomUploadService.uploadBatch(List.of(), 42L));

        assertEquals("At least one file must be provided", ex.getMessage());
        verifyNoInteractions(s3Client);
    }

    @Test
    void uploadBatch_AllFilesShareSameS3Prefix() {
        List<org.springframework.web.multipart.MultipartFile> files = List.of(
                validDicomFile("scan1.dcm"),
                validDicomFile("scan2.dcm")
        );

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        dicomUploadService.uploadBatch(files, 42L);

        verify(s3Client, times(2)).putObject(captor.capture(), any(RequestBody.class));
        List<PutObjectRequest> requests = captor.getAllValues();

        String prefix1 = requests.get(0).key().substring(0, requests.get(0).key().lastIndexOf('/') + 1);
        String prefix2 = requests.get(1).key().substring(0, requests.get(1).key().lastIndexOf('/') + 1);

        assertEquals(prefix1, prefix2, "Both files should share the same S3 prefix");
    }

    @Test
    void uploadBatch_ValidationFailureSkipsAllS3Calls() {
        List<org.springframework.web.multipart.MultipartFile> files = List.of(
                validDicomFile("scan1.dcm"),
                new MockMultipartFile("file", "invalid.dcm", "application/dicom", new byte[50]) // too small
        );

        assertThrows(IllegalArgumentException.class,
                () -> dicomUploadService.uploadBatch(files, 42L));

        verifyNoInteractions(s3Client);
    }


    // sad paths

    @Test
    void upload_ThrowsException_WhenFileIsEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.dcm", "application/dicom", new byte[0]);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dicomUploadService.upload(emptyFile, 42L));

        assertEquals("File must not be empty", ex.getMessage());
    }

    @Test
    void upload_DoesNotCallS3_WhenFileIsEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.dcm", "application/dicom", new byte[0]);

        assertThrows(IllegalArgumentException.class,
                () -> dicomUploadService.upload(emptyFile, 42L));

        verifyNoInteractions(s3Client);
    }

    @Test
    void upload_ThrowsException_WhenFileTooSmallForDicomHeader() {
        MockMultipartFile tooSmall = new MockMultipartFile(
                "file", "tiny.dcm", "application/dicom", new byte[50]);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dicomUploadService.upload(tooSmall, 42L));

        assertEquals("File is too small to be a valid DICOM file", ex.getMessage());
        verifyNoInteractions(s3Client);
    }

    @Test
    void upload_ThrowsException_WhenMagicBytesAreMissing() {
        // create fake file filled with zeros, no "DICM" at byte 128 should fail validation
        MockMultipartFile notDicom = new MockMultipartFile(
                "file", "fake.dcm", "application/dicom", new byte[200]);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dicomUploadService.upload(notDicom, 42L));

        assertEquals("File is not a valid DICOM file", ex.getMessage());

        // make sure nothing was sent to S3 since file is invalid
        verifyNoInteractions(s3Client);
    }

    @Test
    void upload_ThrowsException_WhenMagicBytesArePartiallyCorrect() {
        // only part of "DICM" is present
        byte[] content = new byte[200];
        content[128] = 'D';
        content[129] = 'I';
        // leave last two bytes incorrect on purpose
        MockMultipartFile partialMagic = new MockMultipartFile(
                "file", "partial.dcm", "application/dicom", content);

        assertThrows(IllegalArgumentException.class,
                () -> dicomUploadService.upload(partialMagic, 42L));

        // make sure nothing was sent to S3 since file is invalid
        verifyNoInteractions(s3Client);
    }


    // helper DICOM file

    private MockMultipartFile validDicomFile(String filename) {
        byte[] content = new byte[200];
        content[128] = 'D';
        content[129] = 'I';
        content[130] = 'C';
        content[131] = 'M';
        return new MockMultipartFile("file", filename, "application/dicom", content);
    }
}

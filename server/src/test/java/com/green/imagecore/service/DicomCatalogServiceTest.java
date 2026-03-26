package com.green.imagecore.service;

import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.User;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.DicomImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DicomCatalogServiceTest {

    @Mock
    private DicomImageRepository dicomImageRepository;

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private DicomCatalogService dicomCatalogService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dicomCatalogService, "bucketName", "test-bucket");
    }


    // findAllForUser

    @Test
    void findAllForUser_ReturnsImagesFromRepository() {
        List<DicomImage> expected = List.of(
                buildImage(1L, 42L, "a.dcm", "dicom/42/a.dcm"),
                buildImage(2L, 42L, "b.dcm", "dicom/42/b.dcm")
        );
        when(dicomImageRepository.findByUserIdOrderByUploadedAtDesc(42L)).thenReturn(expected);

        List<DicomImage> result = dicomCatalogService.findAllForUser(42L);

        assertEquals(2, result.size());
        assertEquals("a.dcm", result.get(0).getFilename());
        assertEquals("b.dcm", result.get(1).getFilename());
    }

    @Test
    void findAllForUser_ReturnsEmptyList_WhenNoImages() {
        when(dicomImageRepository.findByUserIdOrderByUploadedAtDesc(99L)).thenReturn(List.of());

        List<DicomImage> result = dicomCatalogService.findAllForUser(99L);

        assertTrue(result.isEmpty());
    }

    @Test
    void findAllForUser_DelegatesToRepositoryWithCorrectUserId() {
        when(dicomImageRepository.findByUserIdOrderByUploadedAtDesc(55L)).thenReturn(List.of());

        dicomCatalogService.findAllForUser(55L);

        verify(dicomImageRepository).findByUserIdOrderByUploadedAtDesc(55L);
    }


    // findByIdAndUser

    @Test
    void findByIdAndUser_ReturnsImage_WhenOwnedByUser() {
        DicomImage image = buildImage(10L, 42L, "scan.dcm", "dicom/42/scan.dcm");
        when(dicomImageRepository.findByIdAndUserId(10L, 42L)).thenReturn(Optional.of(image));

        DicomImage result = dicomCatalogService.findByIdAndUser(10L, 42L);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals("scan.dcm", result.getFilename());
    }

    @Test
    void findByIdAndUser_ThrowsResourceNotFoundException_WhenImageNotFound() {
        when(dicomImageRepository.findByIdAndUserId(999L, 42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> dicomCatalogService.findByIdAndUser(999L, 42L));
    }

    @Test
    void findByIdAndUser_ThrowsResourceNotFoundException_WhenImageBelongsToOtherUser() {
        // image exists but belongs to user 99, not user 42
        when(dicomImageRepository.findByIdAndUserId(10L, 42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> dicomCatalogService.findByIdAndUser(10L, 42L));
    }


    // delete

    @Test
    void delete_CallsS3DeleteWithCorrectKey() {
        DicomImage image = buildImage(5L, 42L, "scan.dcm", "dicom/42/uuid.dcm");
        when(dicomImageRepository.findByIdAndUserId(5L, 42L)).thenReturn(Optional.of(image));

        dicomCatalogService.delete(5L, 42L);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertEquals("test-bucket", captor.getValue().bucket());
        assertEquals("dicom/42/uuid.dcm", captor.getValue().key());
    }

    @Test
    void delete_CallsS3BeforeRepository() {
        DicomImage image = buildImage(5L, 42L, "scan.dcm", "dicom/42/uuid.dcm");
        when(dicomImageRepository.findByIdAndUserId(5L, 42L)).thenReturn(Optional.of(image));

        var order = inOrder(s3Client, dicomImageRepository);
        dicomCatalogService.delete(5L, 42L);
        order.verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        order.verify(dicomImageRepository).delete(image);
    }

    @Test
    void delete_DeletesImageFromRepository() {
        DicomImage image = buildImage(5L, 42L, "scan.dcm", "dicom/42/uuid.dcm");
        when(dicomImageRepository.findByIdAndUserId(5L, 42L)).thenReturn(Optional.of(image));

        dicomCatalogService.delete(5L, 42L);

        verify(dicomImageRepository).delete(image);
    }

    @Test
    void delete_ThrowsResourceNotFoundException_WhenImageNotFound() {
        when(dicomImageRepository.findByIdAndUserId(999L, 42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> dicomCatalogService.delete(999L, 42L));

        verifyNoInteractions(s3Client);
    }

    @Test
    void delete_DoesNotDeleteFromRepository_WhenS3Throws() {
        DicomImage image = buildImage(5L, 42L, "scan.dcm", "dicom/42/uuid.dcm");
        when(dicomImageRepository.findByIdAndUserId(5L, 42L)).thenReturn(Optional.of(image));
        doThrow(new RuntimeException("S3 unavailable")).when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        assertThrows(RuntimeException.class,
                () -> dicomCatalogService.delete(5L, 42L));

        // DB row must remain intact when S3 deletion fails
        verify(dicomImageRepository, never()).delete(any());
    }

    @Test
    void delete_ThrowsResourceNotFoundException_WhenImageBelongsToOtherUser() {
        when(dicomImageRepository.findByIdAndUserId(5L, 42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> dicomCatalogService.delete(5L, 42L));

        verifyNoInteractions(s3Client);
    }


    // helpers

    private DicomImage buildImage(Long id, Long userId, String filename, String s3Key) {
        User user = new User();
        user.setId(userId);

        DicomImage img = new DicomImage();
        img.setId(id);
        img.setUser(user);
        img.setFilename(filename);
        img.setFileSize(1024L);
        img.setS3Key(s3Key);
        img.setUploadedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return img;
    }
}

package com.green.imagecore.service;

import com.green.imagecore.dto.DicomSeriesGroupDto;
import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.ImportStatus;
import com.green.imagecore.entities.User;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.DicomImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.medicalimaging.MedicalImagingClient;
import software.amazon.awssdk.services.medicalimaging.model.DeleteImageSetRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import static org.mockito.Mockito.lenient;

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
    private MedicalImagingClient medicalImagingClient;

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private DicomCatalogService dicomCatalogService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dicomCatalogService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(dicomCatalogService, "datastoreId", "test-datastore-id");
        // Lenient defaults for delete tests — non-delete tests don't use these
        lenient().when(medicalImagingClient.deleteImageSet(any(DeleteImageSetRequest.class))).thenReturn(null);
        lenient().when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenAnswer(inv -> {
            ListObjectsV2Request req = inv.getArgument(0);
            S3Object obj = S3Object.builder().key(req.prefix() + "instance.dcm").build();
            return ListObjectsV2Response.builder().contents(obj).build();
        });
        lenient().when(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(null);
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
    void delete_ListsObjectsUnderPrefixAndDeletesThem() {
        DicomImage image = buildImage(5L, 42L, "series label", "dicom/42/batch-uuid/");
        when(dicomImageRepository.findByIdAndUserId(5L, 42L)).thenReturn(Optional.of(image));

        dicomCatalogService.delete(5L, 42L);

        verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client).deleteObjects(any(DeleteObjectsRequest.class));
        verify(dicomImageRepository).delete(image);
    }

    @Test
    void delete_CallsListObjectsBeforeDeleteObjects() {
        DicomImage image = buildImage(5L, 42L, "series label", "dicom/42/batch-uuid/");
        when(dicomImageRepository.findByIdAndUserId(5L, 42L)).thenReturn(Optional.of(image));

        var order = inOrder(s3Client, dicomImageRepository);
        dicomCatalogService.delete(5L, 42L);
        order.verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
        order.verify(s3Client).deleteObjects(any(DeleteObjectsRequest.class));
        order.verify(dicomImageRepository).delete(image);
    }

    @Test
    void delete_DeletesImageFromRepository() {
        DicomImage image = buildImage(5L, 42L, "series label", "dicom/42/batch-uuid/");
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
        DicomImage image = buildImage(5L, 42L, "series label", "dicom/42/batch-uuid/");
        when(dicomImageRepository.findByIdAndUserId(5L, 42L)).thenReturn(Optional.of(image));
        doThrow(new RuntimeException("S3 unavailable")).when(s3Client).listObjectsV2(any(ListObjectsV2Request.class));

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


    // findSeriesGroupsForUser

    @Test
    void findSeriesGroupsForUser_ReturnsEmptyList_WhenNoImages() {
        when(dicomImageRepository.findByUserIdOrderByUploadedAtDesc(42L)).thenReturn(List.of());

        List<DicomSeriesGroupDto> result = dicomCatalogService.findSeriesGroupsForUser(42L);

        assertTrue(result.isEmpty());
    }

    @Test
    void findSeriesGroupsForUser_ReturnsSingleGroup_ForOneCompletedImage() {
        DicomImage img = buildCompletedImage(1L, 42L, "T1 MPRAGE", "Brain MRI", "imgset-001");
        when(dicomImageRepository.findByUserIdOrderByUploadedAtDesc(42L)).thenReturn(List.of(img));

        List<DicomSeriesGroupDto> result = dicomCatalogService.findSeriesGroupsForUser(42L);

        assertEquals(1, result.size());
        assertEquals("imgset-001", result.get(0).getKey());
        assertEquals("imgset-001", result.get(0).getImageSetId());
        assertEquals("T1 MPRAGE",  result.get(0).getDisplayName());
        assertEquals("COMPLETED",  result.get(0).getStatus());
        assertEquals(List.of(1L),  result.get(0).getImageIds());
    }

    @Test
    void findSeriesGroupsForUser_GroupsImagesWithSameImageSetId() {
        DicomImage img1 = buildCompletedImage(1L, 42L, null, null, "imgset-001");
        DicomImage img2 = buildCompletedImage(2L, 42L, null, null, "imgset-001");
        when(dicomImageRepository.findByUserIdOrderByUploadedAtDesc(42L)).thenReturn(List.of(img1, img2));

        List<DicomSeriesGroupDto> result = dicomCatalogService.findSeriesGroupsForUser(42L);

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getInstanceCount()); // 1 frameCount each
        assertEquals(2, result.get(0).getImageIds().size());
    }

    @Test
    void findSeriesGroupsForUser_CreatesSeparateGroupsForDifferentImageSetIds() {
        DicomImage img1 = buildCompletedImage(1L, 42L, null, null, "imgset-001");
        DicomImage img2 = buildCompletedImage(2L, 42L, null, null, "imgset-002");
        when(dicomImageRepository.findByUserIdOrderByUploadedAtDesc(42L)).thenReturn(List.of(img1, img2));

        List<DicomSeriesGroupDto> result = dicomCatalogService.findSeriesGroupsForUser(42L);

        assertEquals(2, result.size());
    }

    @Test
    void findSeriesGroupsForUser_AssignsPendingKeyForImagesWithoutImageSetId() {
        DicomImage img = new DicomImage();
        img.setId(5L);
        img.setFilename("pending.dcm");
        img.setImportStatus(ImportStatus.PENDING);
        img.setFileCount(1);
        img.setFrameCount(1);
        when(dicomImageRepository.findByUserIdOrderByUploadedAtDesc(42L)).thenReturn(List.of(img));

        List<DicomSeriesGroupDto> result = dicomCatalogService.findSeriesGroupsForUser(42L);

        assertEquals(1, result.size());
        assertEquals("__pending__5", result.get(0).getKey());
        assertNull(result.get(0).getImageSetId());
    }

    @Test
    void findSeriesGroupsForUser_PrioritizesSeriesDescriptionAsDisplayName() {
        DicomImage img = buildCompletedImage(1L, 42L, "Series Desc", "Study Desc", "imgset-001");
        when(dicomImageRepository.findByUserIdOrderByUploadedAtDesc(42L)).thenReturn(List.of(img));

        assertEquals("Series Desc",
                dicomCatalogService.findSeriesGroupsForUser(42L).get(0).getDisplayName());
    }

    @Test
    void findSeriesGroupsForUser_FallsBackToStudyDescriptionWhenNoSeriesDescription() {
        DicomImage img = buildCompletedImage(1L, 42L, null, "Study Desc", "imgset-001");
        when(dicomImageRepository.findByUserIdOrderByUploadedAtDesc(42L)).thenReturn(List.of(img));

        assertEquals("Study Desc",
                dicomCatalogService.findSeriesGroupsForUser(42L).get(0).getDisplayName());
    }

    @Test
    void findSeriesGroupsForUser_FallsBackToImageSetIdWhenNoDescriptions() {
        DicomImage img = buildCompletedImage(1L, 42L, null, null, "imgset-001");
        when(dicomImageRepository.findByUserIdOrderByUploadedAtDesc(42L)).thenReturn(List.of(img));

        assertEquals("imgset-001",
                dicomCatalogService.findSeriesGroupsForUser(42L).get(0).getDisplayName());
    }

    @Test
    void findSeriesGroupsForUser_FallsBackToFilenameWhenNoDescriptionsOrImageSetId() {
        DicomImage img = new DicomImage();
        img.setId(1L);
        img.setFilename("scan.dcm");
        img.setImportStatus(ImportStatus.PENDING);
        img.setFileCount(1);
        img.setFrameCount(1);
        when(dicomImageRepository.findByUserIdOrderByUploadedAtDesc(42L)).thenReturn(List.of(img));

        assertEquals("scan.dcm",
                dicomCatalogService.findSeriesGroupsForUser(42L).get(0).getDisplayName());
    }

    @Test
    void findSeriesGroupsForUser_ComputesInstanceCountAsSumOfFrameCounts() {
        DicomImage img1 = buildCompletedImage(1L, 42L, null, null, "imgset-001");
        img1.setFrameCount(10);
        DicomImage img2 = buildCompletedImage(2L, 42L, null, null, "imgset-001");
        img2.setFrameCount(5);
        when(dicomImageRepository.findByUserIdOrderByUploadedAtDesc(42L)).thenReturn(List.of(img1, img2));

        assertEquals(15,
                dicomCatalogService.findSeriesGroupsForUser(42L).get(0).getInstanceCount());
    }

    @Test
    void findSeriesGroupsForUser_PicksMostAdvancedStatusAcrossGroup() {
        DicomImage img1 = buildCompletedImage(1L, 42L, null, null, "imgset-001");
        img1.setImportStatus(ImportStatus.IN_PROGRESS);
        DicomImage img2 = buildCompletedImage(2L, 42L, null, null, "imgset-001");
        img2.setImportStatus(ImportStatus.COMPLETED);
        when(dicomImageRepository.findByUserIdOrderByUploadedAtDesc(42L)).thenReturn(List.of(img1, img2));

        assertEquals("COMPLETED",
                dicomCatalogService.findSeriesGroupsForUser(42L).get(0).getStatus());
    }

    @Test
    void findSeriesGroupsForUser_CollectsAllImageIdsInGroup() {
        DicomImage img1 = buildCompletedImage(10L, 42L, null, null, "imgset-001");
        DicomImage img2 = buildCompletedImage(11L, 42L, null, null, "imgset-001");
        DicomImage img3 = buildCompletedImage(12L, 42L, null, null, "imgset-001");
        when(dicomImageRepository.findByUserIdOrderByUploadedAtDesc(42L)).thenReturn(List.of(img1, img2, img3));

        assertEquals(List.of(10L, 11L, 12L),
                dicomCatalogService.findSeriesGroupsForUser(42L).get(0).getImageIds());
    }


    // delete — HealthImaging interaction

    @Test
    void delete_CallsHealthImagingDeleteImageSet_WhenImageSetIdIsPresent() {
        DicomImage image = buildImage(5L, 42L, "scan.dcm", "dicom/42/uuid/");
        image.setImageSetId("imgset-001");
        when(dicomImageRepository.findByIdAndUserId(5L, 42L)).thenReturn(Optional.of(image));

        dicomCatalogService.delete(5L, 42L);

        verify(medicalImagingClient).deleteImageSet(any(DeleteImageSetRequest.class));
    }

    @Test
    void delete_SkipsHealthImagingDeletion_WhenImageSetIdIsNull() {
        DicomImage image = buildImage(5L, 42L, "scan.dcm", "dicom/42/uuid/");
        // imageSetId is null by default
        when(dicomImageRepository.findByIdAndUserId(5L, 42L)).thenReturn(Optional.of(image));

        dicomCatalogService.delete(5L, 42L);

        verifyNoInteractions(medicalImagingClient);
    }

    @Test
    void delete_ContinuesToDeleteS3AndDb_WhenHealthImagingThrowsResourceNotFoundException() {
        DicomImage image = buildImage(5L, 42L, "scan.dcm", "dicom/42/uuid/");
        image.setImageSetId("imgset-001");
        when(dicomImageRepository.findByIdAndUserId(5L, 42L)).thenReturn(Optional.of(image));
        when(medicalImagingClient.deleteImageSet(any(DeleteImageSetRequest.class)))
                .thenThrow(software.amazon.awssdk.services.medicalimaging.model.ResourceNotFoundException
                        .builder().message("imageSet already deleted").build());

        // ResourceNotFoundException from HealthImaging is swallowed — no exception should propagate
        dicomCatalogService.delete(5L, 42L);

        verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
        verify(dicomImageRepository).delete(image);
    }

    @Test
    void delete_SkipsDeleteObjects_WhenS3ListReturnsNoContents() {
        DicomImage image = buildImage(5L, 42L, "scan.dcm", "dicom/42/uuid/");
        when(dicomImageRepository.findByIdAndUserId(5L, 42L)).thenReturn(Optional.of(image));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().build()); // empty contents

        dicomCatalogService.delete(5L, 42L);

        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
        verify(dicomImageRepository).delete(image);
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
        img.setFileCount(1);
        img.setS3Key(s3Key);
        img.setUploadedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return img;
    }

    /**
     * Builds a COMPLETED DicomImage with series/study labels and imageSetId set.
     * Uses the Lombok builder so {@code @Builder.Default} for {@code frameCount = 1} applies.
     */
    private DicomImage buildCompletedImage(Long id, Long userId,
                                           String seriesDescription, String studyDescription,
                                           String imageSetId) {
        User user = new User();
        user.setId(userId);

        return DicomImage.builder()
                .id(id)
                .user(user)
                .filename("scan.dcm")
                .fileSize(1024L)
                .fileCount(1)
                .s3Key("dicom/" + userId + "/" + id + "/")
                .importStatus(ImportStatus.COMPLETED)
                .imageSetId(imageSetId)
                .seriesDescription(seriesDescription)
                .studyDescription(studyDescription)
                .build();
    }
}

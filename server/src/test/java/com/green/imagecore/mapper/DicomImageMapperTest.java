package com.green.imagecore.mapper;

import com.green.imagecore.dto.DicomImageDto;
import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DicomImageMapperTest {

    // helpers

    private DicomImage buildImage(Long id, String filename, Long fileSize, String s3Key, Instant uploadedAt) {
        User user = new User();
        user.setId(1L);

        DicomImage img = new DicomImage();
        img.setId(id);
        img.setUser(user);
        img.setFilename(filename);
        img.setFileSize(fileSize);
        img.setS3Key(s3Key);
        img.setUploadedAt(uploadedAt);
        return img;
    }


    // toDto

    @Test
    void toDto_nullImage_returnsNull() {
        assertNull(DicomImageMapper.toDto(null));
    }

    @Test
    void toDto_validImage_mapsAllFieldsCorrectly() {
        Instant now = Instant.parse("2026-01-15T10:30:00Z");
        DicomImage image = buildImage(7L, "brain.dcm", 204800L, "dicom/1/brain.dcm", now);

        DicomImageDto dto = DicomImageMapper.toDto(image);

        assertNotNull(dto);
        assertEquals(7L, dto.getId());
        assertEquals("brain.dcm", dto.getFilename());
        assertEquals(204800L, dto.getFileSize());
        assertEquals(now.toString(), dto.getUploadedAt());
    }

    @Test
    void toDto_nullUploadedAt_setsUploadedAtToNull() {
        DicomImage image = buildImage(1L, "scan.dcm", 1024L, "dicom/1/scan.dcm", null);

        DicomImageDto dto = DicomImageMapper.toDto(image);

        assertNotNull(dto);
        assertNull(dto.getUploadedAt());
    }

    @Test
    void toDto_doesNotExposeSensitiveFields() {
        // DTO must not expose the S3 key (internal storage detail)
        DicomImage image = buildImage(1L, "scan.dcm", 1024L, "dicom/1/internal-key.dcm",
                Instant.now());

        DicomImageDto dto = DicomImageMapper.toDto(image);

        // DicomImageDto has no s3Key field — this test documents the intent
        assertNotNull(dto.getId());
        assertNotNull(dto.getFilename());
        assertNotNull(dto.getFileSize());
    }

    @Test
    void toDto_doesNotMutateOriginalImage() {
        Instant now = Instant.parse("2026-02-01T08:00:00Z");
        DicomImage image = buildImage(3L, "ct.dcm", 512000L, "dicom/1/ct.dcm", now);

        DicomImageMapper.toDto(image);

        assertEquals(3L, image.getId());
        assertEquals("ct.dcm", image.getFilename());
        assertEquals(512000L, image.getFileSize());
    }


    // toDtos

    @Test
    void toDtos_nullList_returnsEmptyList() {
        assertTrue(DicomImageMapper.toDtos(null).isEmpty());
    }

    @Test
    void toDtos_emptyList_returnsEmptyList() {
        assertTrue(DicomImageMapper.toDtos(List.of()).isEmpty());
    }

    @Test
    void toDtos_validList_mapsAllImages() {
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-02T00:00:00Z");
        DicomImage img1 = buildImage(1L, "a.dcm", 100L, "dicom/1/a.dcm", t1);
        DicomImage img2 = buildImage(2L, "b.dcm", 200L, "dicom/1/b.dcm", t2);

        List<DicomImageDto> dtos = DicomImageMapper.toDtos(List.of(img1, img2));

        assertEquals(2, dtos.size());
        assertEquals(1L, dtos.get(0).getId());
        assertEquals("a.dcm", dtos.get(0).getFilename());
        assertEquals(2L, dtos.get(1).getId());
        assertEquals("b.dcm", dtos.get(1).getFilename());
    }

    @Test
    void toDtos_preservesOrderOfInputList() {
        DicomImage img1 = buildImage(1L, "alpha.dcm", 100L, "k1", Instant.now());
        DicomImage img2 = buildImage(2L, "beta.dcm",  200L, "k2", Instant.now());
        DicomImage img3 = buildImage(3L, "gamma.dcm", 300L, "k3", Instant.now());

        List<DicomImageDto> dtos = DicomImageMapper.toDtos(List.of(img1, img2, img3));

        assertEquals("alpha.dcm", dtos.get(0).getFilename());
        assertEquals("beta.dcm",  dtos.get(1).getFilename());
        assertEquals("gamma.dcm", dtos.get(2).getFilename());
    }

    @Test
    void toDtos_listContainingNullImage_includesNullInResult() {
        DicomImage img = buildImage(1L, "scan.dcm", 100L, "k1", Instant.now());
        List<DicomImage> images = new ArrayList<>();
        images.add(img);
        images.add(null);

        List<DicomImageDto> dtos = DicomImageMapper.toDtos(images);

        assertEquals(2, dtos.size());
        assertNotNull(dtos.get(0));
        assertNull(dtos.get(1));
    }

    @Test
    void toDtos_singleItemList_returnsOneDto() {
        DicomImage img = buildImage(5L, "mri.dcm", 300L, "dicom/1/mri.dcm", Instant.now());

        List<DicomImageDto> dtos = DicomImageMapper.toDtos(List.of(img));

        assertEquals(1, dtos.size());
        assertEquals(5L, dtos.get(0).getId());
        assertEquals("mri.dcm", dtos.get(0).getFilename());
    }
}

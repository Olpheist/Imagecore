package com.green.imagecore.mapper;

import com.green.imagecore.dto.DicomImageDto;
import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.ImportStatus;
import com.green.imagecore.entities.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
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
        img.setFileCount(1);
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
        assertEquals(1, dto.getFileCount());
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


    // toDto — DICOM metadata fields

    @Test
    void toDto_mapsDicomMetadataFields() {
        DicomImage image = buildImage(1L, "scan.dcm", 1024L, "dicom/1/scan.dcm", Instant.now());
        image.setImportStatus(ImportStatus.COMPLETED);
        image.setImageSetId("imgset-abc");
        image.setStudyInstanceUid("1.2.3.4.5");
        image.setSeriesInstanceUid("1.2.3.4.5.1");
        image.setSopInstanceUid("1.2.3.4.5.1.1");
        image.setStudyDescription("Brain MRI");
        image.setSeriesDescription("T1 MPRAGE");
        image.setBodyPart("Brain");
        image.setModality("MR");
        image.setPatientId("PT-001");
        image.setStudyDate(LocalDate.of(2026, 3, 1));
        image.setPhysician("Dr. Smith");
        image.setFrameCount(3);
        image.setInstanceNumber(2);

        DicomImageDto dto = DicomImageMapper.toDto(image);

        assertEquals(ImportStatus.COMPLETED, dto.getImportStatus());
        assertEquals("imgset-abc",     dto.getImageSetId());
        assertEquals("1.2.3.4.5",      dto.getStudyInstanceUid());
        assertEquals("1.2.3.4.5.1",    dto.getSeriesInstanceUid());
        assertEquals("1.2.3.4.5.1.1",  dto.getSopInstanceUid());
        assertEquals("Brain MRI",       dto.getStudyDescription());
        assertEquals("T1 MPRAGE",       dto.getSeriesDescription());
        assertEquals("Brain",           dto.getBodyPart());
        assertEquals("MR",              dto.getModality());
        assertEquals("PT-001",          dto.getPatientId());
        assertEquals("2026-03-01",      dto.getStudyDate());
        assertEquals("Dr. Smith",       dto.getPhysician());
        assertEquals(3,                 dto.getFrameCount());
        assertEquals(2,                 dto.getInstanceNumber());
    }

    @Test
    void toDto_parsesSopInstanceUidsFromJsonArray() {
        DicomImage image = buildImage(1L, "scan.dcm", 1024L, "dicom/1/scan.dcm", Instant.now());
        image.setSopInstanceUids("[\"1.2.3.4.5\",\"1.2.3.4.6\",\"1.2.3.4.7\"]");

        DicomImageDto dto = DicomImageMapper.toDto(image);

        assertNotNull(dto.getSopInstanceUids());
        assertEquals(List.of("1.2.3.4.5", "1.2.3.4.6", "1.2.3.4.7"), dto.getSopInstanceUids());
    }

    @Test
    void toDto_handlesNullSopInstanceUids() {
        DicomImage image = buildImage(1L, "scan.dcm", 1024L, "dicom/1/scan.dcm", Instant.now());
        image.setSopInstanceUids(null);

        DicomImageDto dto = DicomImageMapper.toDto(image);

        assertNull(dto.getSopInstanceUids());
    }

    @Test
    void toDto_handlesMalformedSopInstanceUidsGracefully() {
        DicomImage image = buildImage(1L, "scan.dcm", 1024L, "dicom/1/scan.dcm", Instant.now());
        image.setSopInstanceUids("not-valid-json");

        // Should not throw; sopInstanceUids remains null (exception is silently ignored)
        DicomImageDto dto = DicomImageMapper.toDto(image);

        assertNull(dto.getSopInstanceUids());
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

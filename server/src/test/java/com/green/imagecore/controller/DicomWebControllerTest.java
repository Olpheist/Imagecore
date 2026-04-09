package com.green.imagecore.controller;

import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.ImportStatus;
import com.green.imagecore.exception.GlobalExceptionHandler;
import com.green.imagecore.repositories.DicomImageRepository;
import com.green.imagecore.service.HealthImagingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DicomWebControllerTest {

    private MockMvc mockMvc;

    @Mock private DicomImageRepository dicomImageRepository;
    @Mock private HealthImagingService healthImagingService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new DicomWebController(dicomImageRepository, healthImagingService)
        ).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    // ── QIDO-RS: /studies ───────────────────────────────────────────────

    @Test
    void searchStudies_returnsCompletedImagesForAuthenticatedUser() throws Exception {
        when(dicomImageRepository.findByUserIdAndImportStatusOrderByUploadedAtDesc(42L, ImportStatus.COMPLETED))
                .thenReturn(List.of(stubCompletedImage()));

        mockMvc.perform(get("/api/dicomweb/studies").principal(authTokenForUser("42")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/dicom+json"))
                .andExpect(jsonPath("$[0].0020000D.Value[0]").value("1.2.3.4.5"))
                .andExpect(jsonPath("$[0].00100020.Value[0]").value("PT-001"));
    }

    @Test
    void searchStudies_returnsEmptyArrayWhenNoCompletedImages() throws Exception {
        when(dicomImageRepository.findByUserIdAndImportStatusOrderByUploadedAtDesc(42L, ImportStatus.COMPLETED))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/dicomweb/studies").principal(authTokenForUser("42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void searchStudies_scopesQueryToAuthenticatedUserId() throws Exception {
        when(dicomImageRepository.findByUserIdAndImportStatusOrderByUploadedAtDesc(99L, ImportStatus.COMPLETED))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/dicomweb/studies").principal(authTokenForUser("99")))
                .andExpect(status().isOk());

        verify(dicomImageRepository).findByUserIdAndImportStatusOrderByUploadedAtDesc(99L, ImportStatus.COMPLETED);
    }

    // ── QIDO-RS: /studies/{uid}/series ──────────────────────────────────

    @Test
    void searchSeries_returnsSeriesForStudy() throws Exception {
        when(dicomImageRepository.findByUserIdAndStudyInstanceUidAndImportStatus(
                42L, "1.2.3.4.5", ImportStatus.COMPLETED))
                .thenReturn(List.of(stubCompletedImage()));

        mockMvc.perform(get("/api/dicomweb/studies/1.2.3.4.5/series").principal(authTokenForUser("42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].0020000E.Value[0]").value("1.2.3.4.5.1"));
    }

    // ── WADO-RS: /studies/{study}/series/{series}/instances/{sop}/frames/{n} ─

    @Test
    void getFrame_streamsFrameDataForOwnedImage() throws Exception {
        DicomImage image = stubCompletedImage();
        when(dicomImageRepository.findBySopUidForUser("1.2.3.4.5.1.1", 42L))
                .thenReturn(Optional.of(image));
        when(healthImagingService.resolveFrameIdForSop(any(), eq("1.2.3.4.5.1.1"), eq(1))).thenReturn("frame-001");

        mockMvc.perform(get("/api/dicomweb/studies/1.2.3.4.5/series/1.2.3.4.5.1/instances/1.2.3.4.5.1.1/frames/1")
                        .principal(authTokenForUser("42")))
                .andExpect(status().isOk());

        verify(healthImagingService).streamImageFrame(eq("image-set-abc"), eq("frame-001"), any());
    }

    @Test
    void getFrame_streamsCorrectFrameForMultiFrameImage() throws Exception {
        DicomImage image = stubCompletedImage();
        image.setFrameIds("[\"frame-001\",\"frame-002\",\"frame-003\"]");
        image.setFrameCount(3);
        when(dicomImageRepository.findBySopUidForUser("1.2.3.4.5.1.1", 42L))
                .thenReturn(Optional.of(image));
        when(healthImagingService.resolveFrameIdForSop(any(), eq("1.2.3.4.5.1.1"), eq(2))).thenReturn("frame-002");

        mockMvc.perform(get("/api/dicomweb/studies/1.2.3.4.5/series/1.2.3.4.5.1/instances/1.2.3.4.5.1.1/frames/2")
                        .principal(authTokenForUser("42")))
                .andExpect(status().isOk());

        verify(healthImagingService).streamImageFrame(eq("image-set-abc"), eq("frame-002"), any());
    }

    @Test
    void getFrame_returns404WhenImageNotOwnedByUser() throws Exception {
        when(dicomImageRepository.findBySopUidForUser("1.2.3.4.5.1.1", 42L))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/dicomweb/studies/1.2.3.4.5/series/1.2.3.4.5.1/instances/1.2.3.4.5.1.1/frames/1")
                        .principal(authTokenForUser("42")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getFrame_returns404WhenFrameNumberOutOfRange() throws Exception {
        DicomImage image = stubCompletedImage();
        when(dicomImageRepository.findBySopUidForUser("1.2.3.4.5.1.1", 42L))
                .thenReturn(Optional.of(image));
        // resolveFrameIdForSop returns null for out-of-range frame numbers (default mock behaviour)

        mockMvc.perform(get("/api/dicomweb/studies/1.2.3.4.5/series/1.2.3.4.5.1/instances/1.2.3.4.5.1.1/frames/99")
                        .principal(authTokenForUser("42")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getFrame_returns404WhenNoFrameIdAvailable() throws Exception {
        DicomImage image = stubCompletedImage();
        image.setImageFrameId(null);
        image.setFrameIds(null);
        when(dicomImageRepository.findBySopUidForUser("1.2.3.4.5.1.1", 42L))
                .thenReturn(Optional.of(image));
        // resolveFrameIdForSop returns null when both frameIds and imageFrameId are null (default mock)

        mockMvc.perform(get("/api/dicomweb/studies/1.2.3.4.5/series/1.2.3.4.5.1/instances/1.2.3.4.5.1.1/frames/1")
                        .principal(authTokenForUser("42")))
                .andExpect(status().isNotFound());
    }

    // ── WADO-RS: metadata ───────────────────────────────────────────────

    @Test
    void getInstanceMetadata_returnsMetadataForOwnedInstance() throws Exception {
        when(dicomImageRepository.findBySopUidForUser("1.2.3.4.5.1.1", 42L))
                .thenReturn(Optional.of(stubCompletedImage()));

        mockMvc.perform(get("/api/dicomweb/studies/1.2.3.4.5/series/1.2.3.4.5.1/instances/1.2.3.4.5.1.1/metadata")
                        .principal(authTokenForUser("42")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/dicom+json"))
                .andExpect(jsonPath("$[0].00080018.Value[0]").value("1.2.3.4.5.1.1"));
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private JwtAuthenticationToken authTokenForUser(String userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("uid")).thenReturn(userId);
        JwtAuthenticationToken token = mock(JwtAuthenticationToken.class);
        when(token.getToken()).thenReturn(jwt);
        return token;
    }

    private DicomImage stubCompletedImage() {
        DicomImage img = new DicomImage();
        img.setId(1L);
        img.setFilename("scan.dcm");
        img.setFileSize(1024L);
        img.setImportStatus(ImportStatus.COMPLETED);
        img.setImageSetId("image-set-abc");
        img.setS3Key("dicom/42/test-uuid/instance.dcm");
        img.setUploadedAt(Instant.parse("2026-01-01T00:00:00Z"));
        img.setStudyInstanceUid("1.2.3.4.5");
        img.setSeriesInstanceUid("1.2.3.4.5.1");
        img.setSopInstanceUid("1.2.3.4.5.1.1");
        img.setStudyDescription("Brain MRI");
        img.setSeriesDescription("T1 MPRAGE");
        img.setModality("MR");
        img.setBodyPart("Brain");
        img.setPatientId("PT-001");
        img.setPhysician("Dr. Smith");
        img.setStudyDate(LocalDate.of(2026, 1, 1));
        img.setFrameCount(1);
        img.setImageFrameId("frame-001");
        img.setFrameIds("[\"frame-001\"]");
        img.setInstanceNumber(1);
        return img;
    }
}

package com.green.imagecore.controller;

import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.service.DicomCatalogService;
import com.green.imagecore.service.DicomUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ImageControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DicomUploadService dicomUploadService;

    @Mock
    private DicomCatalogService dicomCatalogService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ImageController(dicomUploadService, dicomCatalogService)
        ).build();
    }


    // POST /upload

    @Test
    void upload_Returns201Created_WithImageDto() throws Exception {
        when(dicomUploadService.upload(any(MultipartFile.class), anyLong()))
                .thenReturn(stubImage(1L, "scan.dcm", 200L, "dicom/42/abc.dcm"));

        mockMvc.perform(multipart("/api/images/upload")
                        .file(anyDicomFile("file"))
                        .principal(authTokenForUser("42")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.filename").value("scan.dcm"));
    }

    @Test
    void upload_PassesUserIdFromJwtUidClaimToService() throws Exception {
        when(dicomUploadService.upload(any(MultipartFile.class), anyLong()))
                .thenReturn(stubImage(1L, "scan.dcm", 200L, "dicom/99/abc.dcm"));

        mockMvc.perform(multipart("/api/images/upload")
                        .file(anyDicomFile("file"))
                        .principal(authTokenForUser("99")))
                .andExpect(status().isCreated());

        verify(dicomUploadService).upload(any(MultipartFile.class), eq(99L));
    }


    // POST /upload-batch

    @Test
    void uploadBatch_Returns201Created_WithListOfImageDtos() throws Exception {
        when(dicomUploadService.uploadBatch(anyList(), anyLong()))
                .thenReturn(List.of(
                        stubImage(1L, "scan1.dcm", 200L, "dicom/42/abc1.dcm"),
                        stubImage(2L, "scan2.dcm", 200L, "dicom/42/abc2.dcm")
                ));

        mockMvc.perform(multipart("/api/images/upload-batch")
                        .file(anyDicomFile("files"))
                        .file(anyDicomFile("files"))
                        .principal(authTokenForUser("42")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void uploadBatch_PassesUserIdFromJwtToService() throws Exception {
        when(dicomUploadService.uploadBatch(anyList(), anyLong())).thenReturn(List.of());

        mockMvc.perform(multipart("/api/images/upload-batch")
                        .file(anyDicomFile("files"))
                        .principal(authTokenForUser("77")))
                .andExpect(status().isCreated());

        verify(dicomUploadService).uploadBatch(anyList(), eq(77L));
    }


    // GET /api/images

    @Test
    void listImages_Returns200_WithListOfImageDtos() throws Exception {
        when(dicomCatalogService.findAllForUser(42L))
                .thenReturn(List.of(
                        stubImage(1L, "a.dcm", 100L, "dicom/42/a.dcm"),
                        stubImage(2L, "b.dcm", 200L, "dicom/42/b.dcm")
                ));

        mockMvc.perform(get("/api/images").principal(authTokenForUser("42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].filename").value("a.dcm"))
                .andExpect(jsonPath("$[1].filename").value("b.dcm"));
    }

    @Test
    void listImages_PassesUserIdFromJwtToService() throws Exception {
        when(dicomCatalogService.findAllForUser(anyLong())).thenReturn(List.of());

        mockMvc.perform(get("/api/images").principal(authTokenForUser("77")))
                .andExpect(status().isOk());

        verify(dicomCatalogService).findAllForUser(77L);
    }

    @Test
    void listImages_Returns200WithEmptyList_WhenNoImages() throws Exception {
        when(dicomCatalogService.findAllForUser(anyLong())).thenReturn(List.of());

        mockMvc.perform(get("/api/images").principal(authTokenForUser("42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }


    // DELETE /api/images/{id}

    @Test
    void deleteImage_Returns204_WhenImageDeleted() throws Exception {
        mockMvc.perform(delete("/api/images/5").principal(authTokenForUser("42")))
                .andExpect(status().isNoContent());

        verify(dicomCatalogService).delete(5L, 42L);
    }

    @Test
    void deleteImage_PassesImageIdAndUserIdToService() throws Exception {
        mockMvc.perform(delete("/api/images/10").principal(authTokenForUser("55")))
                .andExpect(status().isNoContent());

        verify(dicomCatalogService).delete(10L, 55L);
    }


    // Helpers

    /**
     * Builds a mock JwtAuthenticationToken whose "uid" claim returns the given userId.
     * Note: @PreAuthorize is not active in standaloneSetup, so role enforcement
     * is covered by the Cucumber integration tests instead.
     */
    private JwtAuthenticationToken authTokenForUser(String userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("uid")).thenReturn(userId);

        JwtAuthenticationToken token = mock(JwtAuthenticationToken.class);
        when(token.getToken()).thenReturn(jwt);
        return token;
    }

    private MockMultipartFile anyDicomFile(String paramName) {
        return new MockMultipartFile(paramName, "scan.dcm", "application/dicom", new byte[200]);
    }

    private DicomImage stubImage(Long id, String filename, Long fileSize, String s3Key) {
        DicomImage img = new DicomImage();
        img.setId(id);
        img.setFilename(filename);
        img.setFileSize(fileSize);
        img.setS3Key(s3Key);
        img.setUploadedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return img;
    }
}

package com.green.imagecore.controller;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ImageControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DicomUploadService dicomUploadService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ImageController(dicomUploadService)
        ).build();
    }

    @Test
    void upload_Returns201Created_WithKeyFromService() throws Exception {
        when(dicomUploadService.upload(any(MultipartFile.class), anyString()))
                .thenReturn("dicom/42/abc-uuid.dcm");

        mockMvc.perform(multipart("/api/images/upload")
                        .file(anyDicomFile())
                        .principal(authTokenForUser("42")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("dicom/42/abc-uuid.dcm"));
    }

    @Test
    void upload_PassesUserIdFromJwtUidClaimToService() throws Exception {
        when(dicomUploadService.upload(any(MultipartFile.class), anyString()))
                .thenReturn("dicom/99/some-uuid.dcm");

        mockMvc.perform(multipart("/api/images/upload")
                        .file(anyDicomFile())
                        .principal(authTokenForUser("99")))
                .andExpect(status().isCreated());

        verify(dicomUploadService).upload(any(MultipartFile.class), eq("99"));
    }

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

    private MockMultipartFile anyDicomFile() {
        return new MockMultipartFile("file", "scan.dcm", "application/dicom", new byte[200]);
    }
}

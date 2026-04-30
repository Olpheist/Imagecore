package com.green.imagecore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.imagecore.dto.PipelineDto;
import com.green.imagecore.dto.PipelineStepDto;
import com.green.imagecore.service.PipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PipelineControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PipelineService pipelineService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PipelineController(pipelineService)
        ).build();
    }


    // POST /api/images/{imageId}/pipelines

    @Test
    void submitPipeline_Returns201Created_WithPipelineDto() throws Exception {
        when(pipelineService.submitPipeline(eq(1L), anyList(), eq(42L)))
                .thenReturn(stubPipelineDto(10L, "RUNNING"));

        String body = objectMapper.writeValueAsString(
                new PipelineController.SubmitPipelineRequest(List.of(5L, 6L)));

        mockMvc.perform(post("/api/images/1/pipelines")
                        .contentType("application/json")
                        .content(body)
                        .principal(authTokenForUser("42")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.steps.length()").value(2));
    }

    @Test
    void submitPipeline_PassesImageIdToolIdsAndUserIdToService() throws Exception {
        when(pipelineService.submitPipeline(anyLong(), anyList(), anyLong()))
                .thenReturn(stubPipelineDto(10L, "RUNNING"));

        String body = objectMapper.writeValueAsString(
                new PipelineController.SubmitPipelineRequest(List.of(5L, 6L)));

        mockMvc.perform(post("/api/images/3/pipelines")
                        .contentType("application/json")
                        .content(body)
                        .principal(authTokenForUser("99")))
                .andExpect(status().isCreated());

        verify(pipelineService).submitPipeline(3L, List.of(5L, 6L), 99L);
    }


    // GET /api/images/{imageId}/pipelines/latest

    @Test
    void getLatestPipeline_Returns200WithDto_WhenPipelineExists() throws Exception {
        when(pipelineService.findLatestForImage(eq(1L), eq(42L)))
                .thenReturn(Optional.of(stubPipelineDto(10L, "RUNNING")));

        mockMvc.perform(get("/api/images/1/pipelines/latest")
                        .principal(authTokenForUser("42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void getLatestPipeline_Returns404_WhenNoPipelineExists() throws Exception {
        when(pipelineService.findLatestForImage(eq(1L), eq(42L))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/images/1/pipelines/latest")
                        .principal(authTokenForUser("42")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getLatestPipeline_PassesCorrectIdsToService() throws Exception {
        when(pipelineService.findLatestForImage(anyLong(), anyLong()))
                .thenReturn(Optional.of(stubPipelineDto(5L, "COMPLETED")));

        mockMvc.perform(get("/api/images/3/pipelines/latest")
                        .principal(authTokenForUser("99")))
                .andExpect(status().isOk());

        verify(pipelineService).findLatestForImage(3L, 99L);
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

    private PipelineDto stubPipelineDto(Long id, String status) {
        PipelineDto dto = new PipelineDto();
        dto.setId(id);
        dto.setImageId(10L);
        dto.setStatus(status);

        PipelineStepDto step0 = new PipelineStepDto();
        step0.setId(100L);
        step0.setStepOrder(0);
        step0.setToolId(5L);
        step0.setToolName("N4 Bias Field Correction");
        step0.setInputImageId(10L);
        step0.setStatus("RUNNING");

        PipelineStepDto step1 = new PipelineStepDto();
        step1.setId(101L);
        step1.setStepOrder(1);
        step1.setToolId(6L);
        step1.setToolName("Otsu Threshold");
        step1.setInputImageId(10L);
        step1.setStatus("PENDING");

        dto.setSteps(List.of(step0, step1));
        return dto;
    }
}

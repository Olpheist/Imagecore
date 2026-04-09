package com.green.imagecore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.imagecore.entities.Tool;
import com.green.imagecore.entities.User;
import com.green.imagecore.entities.subscription.SubscriptionTierCode;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.service.ToolService;
import com.green.imagecore.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ToolController.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-long-enough-for-hmac")
class ToolControllerTest extends BaseControllerTest {

    @MockitoBean
    private ToolService toolService;

    @MockitoBean
    private UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Tool brainSegmentation;
    private Tool lungNoduleDetector;
    private User ownerUser;

    @BeforeEach
    void setUp() {
        ownerUser = new User();
        ownerUser.setId(1L);
        ownerUser.setUsername("dr.smith");

        brainSegmentation = new Tool();
        brainSegmentation.setToolId(1L);
        brainSegmentation.setName("brain-segmentation");
        brainSegmentation.setCategory("segmentation");
        brainSegmentation.setDescription("Segments brain MRI regions");
        brainSegmentation.setImageTag("123456789.dkr.ecr.us-east-1.amazonaws.com/brain-seg:v1.2.0");
        brainSegmentation.setCreatedBy(ownerUser);

        lungNoduleDetector = new Tool();
        lungNoduleDetector.setToolId(2L);
        lungNoduleDetector.setName("lung-nodule-detector");
        lungNoduleDetector.setCategory("detection");
        lungNoduleDetector.setDescription("Detects pulmonary nodules in CT");
        lungNoduleDetector.setImageTag("123456789.dkr.ecr.us-east-1.amazonaws.com/lung-nd:v2.0.1");
        lungNoduleDetector.setCreatedBy(ownerUser);
    }


    // GET /api/tools
    @Test
    @WithMockUser(roles = "CLINICIAN")
    void getAllTools_asClinician_returns200() throws Exception {
        when(toolService.findAll()).thenReturn(List.of(brainSegmentation, lungNoduleDetector));

        mockMvc.perform(get("/api/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].toolId").value(1))
                .andExpect(jsonPath("$[0].name").value("brain-segmentation"))
                .andExpect(jsonPath("$[0].category").value("segmentation"))
                .andExpect(jsonPath("$[0].imageTag").value("123456789.dkr.ecr.us-east-1.amazonaws.com/brain-seg:v1.2.0"))
                .andExpect(jsonPath("$[1].toolId").value(2))
                .andExpect(jsonPath("$[1].name").value("lung-nodule-detector"));
    }

    @Test
    @WithMockUser(roles = "CLINICIAN")
    void getAllTools_emptyRegistry_returns200WithEmptyList() throws Exception {
        when(toolService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllTools_asAdmin_returns200() throws Exception {
        when(toolService.findAll()).thenReturn(List.of());
        mockMvc.perform(get("/api/tools"))
                .andExpect(status().isOk());
    }


    @Test
    @WithMockUser(roles = "PATIENT")
    void getAllTools_asPatient_returns403() throws Exception {
        mockMvc.perform(get("/api/tools"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(toolService);
    }

    @Test
    void getAllTools_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/tools"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(toolService);
    }


    // GET /api/tools/category/{category}
    @Test
    @WithMockUser(roles = "CLINICIAN")
    void getToolsByCategory_asClinician_returns200() throws Exception {
        when(toolService.findByCategory("segmentation")).thenReturn(List.of(brainSegmentation));

        mockMvc.perform(get("/api/tools/category/segmentation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("brain-segmentation"))
                .andExpect(jsonPath("$[0].category").value("segmentation"));
    }

    @Test
    @WithMockUser(roles = "CLINICIAN")
    void getToolsByCategory_unknownCategory_returns200WithEmptyList() throws Exception {
        when(toolService.findByCategory("unknown")).thenReturn(List.of());

        mockMvc.perform(get("/api/tools/category/unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void getToolsByCategory_asPatient_returns403() throws Exception {
        mockMvc.perform(get("/api/tools/category/segmentation"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(toolService);
    }

    @Test
    void getToolsByCategory_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/tools/category/segmentation"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(toolService);
    }


    // POST /api/tools
    @Test
    @WithMockUser(username = "dr.smith", roles = "ADMIN")
    void createTool_asAdmin_returns200() throws Exception {
        when(userService.findByUsername("dr.smith")).thenReturn(ownerUser);
        when(toolService.create(
                "brain-segmentation",
                ownerUser,
                "segmentation",
                "Segments brain MRI regions",
                "123456789.dkr.ecr.us-east-1.amazonaws.com/brain-seg:v1.2.0",
                null,
                null,
                SubscriptionTierCode.FREE
        )).thenReturn(brainSegmentation);

        String body = objectMapper.writeValueAsString(new ToolController.CreateToolRequest(
                "brain-segmentation",
                ownerUser,
                "segmentation",
                "Segments brain MRI regions",
                "123456789.dkr.ecr.us-east-1.amazonaws.com/brain-seg:v1.2.0",
                null,
                null
        ));

        mockMvc.perform(post("/api/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolId").value(1))
                .andExpect(jsonPath("$.name").value("brain-segmentation"))
                .andExpect(jsonPath("$.category").value("segmentation"))
                .andExpect(jsonPath("$.imageTag").value("123456789.dkr.ecr.us-east-1.amazonaws.com/brain-seg:v1.2.0"));
    }

    @Test
    @WithMockUser(username = "dr.smith", roles = "RESEARCHER")
    void createTool_asResearcher_returns200() throws Exception {
        when(userService.findByUsername("dr.smith")).thenReturn(ownerUser);
        when(toolService.create(
                "brain-segmentation",
                ownerUser,
                "segmentation",
                "Segments brain MRI regions",
                "123456789.dkr.ecr.us-east-1.amazonaws.com/brain-seg:v1.2.0",
                null,
                null,
                SubscriptionTierCode.FREE
        )).thenReturn(brainSegmentation);

        String body = objectMapper.writeValueAsString(new ToolController.CreateToolRequest(
                "brain-segmentation",
                ownerUser,
                "segmentation",
                "Segments brain MRI regions",
                "123456789.dkr.ecr.us-east-1.amazonaws.com/brain-seg:v1.2.0",
                null,
                null
        ));

        mockMvc.perform(post("/api/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolId").value(1))
                .andExpect(jsonPath("$.name").value("brain-segmentation"));
    }

    @Test
    @WithMockUser(username = "dr.smith", roles = "ADMIN")
    void createTool_withOptionalFieldsOmitted_returns200() throws Exception {
        Tool minimalTool = new Tool();
        minimalTool.setToolId(3L);
        minimalTool.setName("minimal-tool");
        minimalTool.setCategory("detection");
        minimalTool.setCreatedBy(ownerUser);

        when(userService.findByUsername("dr.smith")).thenReturn(ownerUser);
        when(toolService.create("minimal-tool", ownerUser, "detection", null, null, null, null, SubscriptionTierCode.FREE))
                .thenReturn(minimalTool);

        String body = objectMapper.writeValueAsString(
                new ToolController.CreateToolRequest("minimal-tool", ownerUser, "detection", null, null, null, null)
        );

        mockMvc.perform(post("/api/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolId").value(3))
                .andExpect(jsonPath("$.name").value("minimal-tool"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.imageTag").doesNotExist());
    }

    @Test
    @WithMockUser(username = "dr.smith", roles = "ADMIN")
    void createTool_duplicateName_returns400() throws Exception {
        when(userService.findByUsername("dr.smith")).thenReturn(ownerUser);
        when(toolService.create(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Name already in use"));

        String body = objectMapper.writeValueAsString(
                new ToolController.CreateToolRequest("brain-segmentation", ownerUser, "segmentation", null, null, null, null)
        );

        mockMvc.perform(post("/api/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Name already in use"));
    }

    @Test
    @WithMockUser(roles = "CLINICIAN")
    void createTool_asClinician_returns200() throws Exception {
        when(userService.findByUsername("dr.smith")).thenReturn(ownerUser);
        when(toolService.create(
                "brain-segmentation",
                ownerUser,
                "segmentation",
                "Segments brain MRI regions",
                "123456789.dkr.ecr.us-east-1.amazonaws.com/brain-seg:v1.2.0",
                null,
                null,
                SubscriptionTierCode.FREE
        )).thenReturn(brainSegmentation);

        String body = objectMapper.writeValueAsString(new ToolController.CreateToolRequest(
                "brain-segmentation",
                ownerUser,
                "segmentation",
                "Segments brain MRI regions",
                "123456789.dkr.ecr.us-east-1.amazonaws.com/brain-seg:v1.2.0",
                null,
                null
        ));

        mockMvc.perform(post("/api/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void createTool_asPatient_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(
                new ToolController.CreateToolRequest("brain-segmentation", ownerUser, "segmentation", null, null, null, null)
        );

        mockMvc.perform(post("/api/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(toolService);
    }

    @Test
    void createTool_unauthenticated_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(
                new ToolController.CreateToolRequest("brain-segmentation", ownerUser, "segmentation", null, null, null, null)
        );

        mockMvc.perform(post("/api/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(toolService);
    }

    // DELETE /api/tools/{id}

    @Test
    @WithMockUser(username = "adminUser", roles = "ADMIN")
    void deleteTool_asAdmin_returns204() throws Exception {
        doNothing().when(toolService).delete(eq(1L), any());

        mockMvc.perform(delete("/api/tools/1"))
                .andExpect(status().isNoContent());

        verify(toolService).delete(eq(1L), any());
    }

    @Test
    @WithMockUser(username = "dr.smith", roles = "CLINICIAN")
    void deleteTool_asOwner_returns204() throws Exception {
        doNothing().when(toolService).delete(eq(1L), any());

        mockMvc.perform(delete("/api/tools/1"))
                .andExpect(status().isNoContent());

        verify(toolService).delete(eq(1L), any());
    }

    @Test
    @WithMockUser(username = "dr.jones", roles = "CLINICIAN")
    void deleteTool_asNonOwner_returns403() throws Exception {
        doThrow(new AccessDeniedException("Access denied"))
                .when(toolService).delete(eq(1L), any());

        mockMvc.perform(delete("/api/tools/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    @WithMockUser(username = "adminUser", roles = "ADMIN")
    void deleteTool_nonExistentId_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Tool not found with id: 999"))
                .when(toolService).delete(eq(999L), any());

        mockMvc.perform(delete("/api/tools/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Tool not found with id: 999"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void deleteTool_asPatient_returns403() throws Exception {
        mockMvc.perform(delete("/api/tools/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(toolService);
    }

    @Test
    void deleteTool_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/tools/1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(toolService);
    }
}
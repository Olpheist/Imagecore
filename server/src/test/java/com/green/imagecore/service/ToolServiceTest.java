package com.green.imagecore.service;

import com.green.imagecore.entities.Tool;
import com.green.imagecore.entities.User;
import com.green.imagecore.entities.subscription.SubscriptionTier;
import com.green.imagecore.entities.subscription.SubscriptionTierCode;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.ToolRepository;
import com.green.imagecore.repositories.subscription.SubscriptionTierRepository;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolService Unit Tests")
class ToolServiceTest {

    @Mock
    private ToolRepository toolRepository;

    @Mock
    private SubscriptionTierRepository subscriptionTierRepository;

    @InjectMocks
    private ToolService toolService;

    private Tool brainSegmentation;
    private Tool lungNoduleDetector;
    private Tool spineSegmentation;

    private User ownerUser;
    private User otherUser;

    private Authentication ownerAuth;
    private Authentication adminAuth;
    private Authentication otherAuth;

    private SubscriptionTier freeTier;

    @BeforeEach
    void setUp() {
        ownerUser = new User();
        ownerUser.setId(1L);
        ownerUser.setUsername("dr.smith");

        otherUser = new User();
        otherUser.setId(2L);
        otherUser.setUsername("dr.jones");

        ownerAuth = new UsernamePasswordAuthenticationToken(
                "dr.smith", null,
                List.of(new SimpleGrantedAuthority("ROLE_CLINICIAN"))
        );

        adminAuth = new UsernamePasswordAuthenticationToken(
                "adminUser", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        otherAuth = new UsernamePasswordAuthenticationToken(
                "dr.jones", null,
                List.of(new SimpleGrantedAuthority("ROLE_CLINICIAN"))
        );

        freeTier = new SubscriptionTier();
        freeTier.setCode(SubscriptionTierCode.FREE);

        brainSegmentation = new Tool();
        brainSegmentation.setToolId(1L);
        brainSegmentation.setName("brain-segmentation");
        brainSegmentation.setCategory("segmentation");
        brainSegmentation.setDescription("Segments brain MRI regions");
        brainSegmentation.setImageTag("123456789.dkr.ecr.us-east-1.amazonaws.com/brain-seg:v1.2.0");
        brainSegmentation.setCreatedBy(ownerUser);
        brainSegmentation.setRequiredTier(freeTier);

        lungNoduleDetector = new Tool();
        lungNoduleDetector.setToolId(2L);
        lungNoduleDetector.setName("lung-nodule-detector");
        lungNoduleDetector.setCategory("detection");
        lungNoduleDetector.setDescription("Detects pulmonary nodules in CT");
        lungNoduleDetector.setImageTag("123456789.dkr.ecr.us-east-1.amazonaws.com/lung-nd:v2.0.1");
        lungNoduleDetector.setCreatedBy(ownerUser);

        spineSegmentation = new Tool();
        spineSegmentation.setToolId(3L);
        spineSegmentation.setName("spine-segmentation");
        spineSegmentation.setCategory("segmentation");
        spineSegmentation.setDescription("Segments vertebrae from lumbar MRI scans");
        spineSegmentation.setImageTag("123456789.dkr.ecr.us-east-1.amazonaws.com/spine-seg:v1.0.0");
        spineSegmentation.setCreatedBy(ownerUser);
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("creates and returns a tool when name is unique")
        void createsToolWhenNameIsUnique() {
            when(toolRepository.existsByName("brain-segmentation")).thenReturn(false);
            when(subscriptionTierRepository.findByCode(SubscriptionTierCode.FREE))
                    .thenReturn(Optional.of(freeTier));
            when(toolRepository.save(any(Tool.class))).thenReturn(brainSegmentation);

            Tool result = toolService.create(
                    "brain-segmentation",
                    ownerUser,
                    "segmentation",
                    "Segments brain MRI regions",
                    "123456789.dkr.ecr.us-east-1.amazonaws.com/brain-seg:v1.2.0",
                    null,
                    null,
                    SubscriptionTierCode.FREE
            );

            assertThat(result.getName()).isEqualTo("brain-segmentation");
            assertThat(result.getCategory()).isEqualTo("segmentation");
            assertThat(result.getImageTag()).isEqualTo("123456789.dkr.ecr.us-east-1.amazonaws.com/brain-seg:v1.2.0");
            assertThat(result.getCreatedBy().getUsername()).isEqualTo("dr.smith");
            assertThat(result.getRequiredTier()).isEqualTo(freeTier);
            verify(toolRepository).save(any(Tool.class));
        }

        @Test
        @DisplayName("creates a tool successfully when optional fields are null")
        void createsToolWithNullOptionalFields() {
            Tool minimalTool = new Tool();
            minimalTool.setToolId(4L);
            minimalTool.setName("minimal-tool");
            minimalTool.setCategory("detection");
            minimalTool.setCreatedBy(ownerUser);
            minimalTool.setRequiredTier(freeTier);

            when(toolRepository.existsByName("minimal-tool")).thenReturn(false);
            when(subscriptionTierRepository.findByCode(SubscriptionTierCode.FREE))
                    .thenReturn(Optional.of(freeTier));
            when(toolRepository.save(any(Tool.class))).thenReturn(minimalTool);

            Tool result = toolService.create("minimal-tool", ownerUser, "detection", null, null, null, null, SubscriptionTierCode.FREE);

            assertThat(result.getName()).isEqualTo("minimal-tool");
            assertThat(result.getDescription()).isNull();
            assertThat(result.getImageTag()).isNull();
            assertThat(result.getCreatedBy()).isEqualTo(ownerUser);
            assertThat(result.getRequiredTier()).isEqualTo(freeTier);
            verify(toolRepository).save(any(Tool.class));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when name is already in use")
        void throwsWhenNameAlreadyExists() {
            when(toolRepository.existsByName("brain-segmentation")).thenReturn(true);

            assertThatThrownBy(() -> toolService.create(
                    "brain-segmentation", ownerUser, "segmentation", null, null, null, null, SubscriptionTierCode.FREE
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("brain-segmentation");

            verify(toolRepository, never()).save(any(Tool.class));
        }
    }


    // delete()
    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("owner can delete their own tool")
        void ownerCanDeleteTheirOwnTool() {
            when(toolRepository.findById(1L)).thenReturn(Optional.of(brainSegmentation));

            toolService.delete(1L, ownerAuth);

            verify(toolRepository).delete(brainSegmentation);
        }

        @Test
        @DisplayName("admin can delete any tool regardless of ownership")
        void adminCanDeleteAnyTool() {
            when(toolRepository.findById(1L)).thenReturn(Optional.of(brainSegmentation));

            toolService.delete(1L, adminAuth);

            verify(toolRepository).delete(brainSegmentation);
        }

        @Test
        @DisplayName("non-owner non-admin throws AccessDeniedException")
        void nonOwnerNonAdminCannotDelete() {
            when(toolRepository.findById(1L)).thenReturn(Optional.of(brainSegmentation));

            assertThatThrownBy(() -> toolService.delete(1L, otherAuth))
                    .isInstanceOf(AccessDeniedException.class);

            verify(toolRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when tool does not exist")
        void throwsResourceNotFoundExceptionWhenNotExists() {
            when(toolRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> toolService.delete(999L, adminAuth))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");

            verify(toolRepository, never()).delete(any());
        }
    }
}
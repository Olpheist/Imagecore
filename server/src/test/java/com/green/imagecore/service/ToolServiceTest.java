package com.green.imagecore.service;

import com.green.imagecore.entities.Tool;
import com.green.imagecore.entities.User;
import com.green.imagecore.entities.subscription.SubscriptionTier;
import com.green.imagecore.entities.subscription.SubscriptionTierCode;
import com.green.imagecore.repositories.ToolRepository;
import com.green.imagecore.repositories.subscription.SubscriptionTierRepository;
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

            Tool result = toolService.create(
                    "minimal-tool",
                    ownerUser,
                    "detection",
                    null,
                    null,
                    SubscriptionTierCode.FREE
            );

            assertThat(result.getName()).isEqualTo("minimal-tool");
            assertThat(result.getDescription()).isNull();
            assertThat(result.getImageTag()).isNull();
            assertThat(result.getCreatedBy()).isEqualTo(ownerUser);
            assertThat(result.getRequiredTier()).isEqualTo(freeTier);
            verify(toolRepository).save(any(Tool.class));
        }
    }
}
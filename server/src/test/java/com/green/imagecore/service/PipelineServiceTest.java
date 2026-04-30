package com.green.imagecore.service;

import com.green.imagecore.dto.PipelineDto;
import com.green.imagecore.entities.*;
import com.green.imagecore.events.AnalysisJobCompletedEvent;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PipelineService Unit Tests")
class PipelineServiceTest {

    @Mock private PipelineRepository     pipelineRepository;
    @Mock private PipelineStepRepository pipelineStepRepository;
    @Mock private DicomImageRepository   dicomImageRepository;
    @Mock private ToolRepository         toolRepository;
    @Mock private UserRepository         userRepository;
    @Mock private AnalysisJobService     analysisJobService;

    @InjectMocks
    private PipelineService pipelineService;

    private DicomImage image;
    private Tool       tool1;
    private Tool       tool2;
    private User       user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);

        image = new DicomImage();
        image.setId(10L);
        image.setImageSetId("img-set-abc");
        image.setS3Key("dicom/1/scan-abc/");

        tool1 = new Tool();
        tool1.setToolId(5L);
        tool1.setName("N4 Bias Field Correction");

        tool2 = new Tool();
        tool2.setToolId(6L);
        tool2.setName("Otsu Threshold");
    }


    // submitPipeline()

    @Nested
    @DisplayName("submitPipeline()")
    class SubmitPipeline {

        @Test
        @DisplayName("creates pipeline with RUNNING status and submits step 0 to ECS immediately")
        void createsPipelineAndSubmitsFirstStep() {
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(image));
            when(userRepository.getReferenceById(1L)).thenReturn(user);
            when(toolRepository.findById(5L)).thenReturn(Optional.of(tool1));
            when(toolRepository.findById(6L)).thenReturn(Optional.of(tool2));
            when(pipelineRepository.save(any())).thenAnswer(inv -> {
                Pipeline p = inv.getArgument(0);
                p.setId(99L);
                return p;
            });
            when(pipelineStepRepository.save(any())).thenAnswer(inv -> {
                PipelineStep s = inv.getArgument(0);
                if (s.getId() == null) s.setId(s.getStepOrder() == 0 ? 200L : 201L);
                return s;
            });
            when(analysisJobService.submit(10L, 5L, 1L)).thenReturn(buildJob(42L));

            PipelineDto dto = pipelineService.submitPipeline(10L, List.of(5L, 6L), 1L);

            assertThat(dto.getStatus()).isEqualTo("RUNNING");
            assertThat(dto.getSteps()).hasSize(2);
            assertThat(dto.getSteps().get(0).getStatus()).isEqualTo("RUNNING");
            assertThat(dto.getSteps().get(1).getStatus()).isEqualTo("PENDING");

            verify(analysisJobService).submit(10L, 5L, 1L);
            verify(analysisJobService, never()).submit(eq(10L), eq(6L), anyLong());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when image is not owned by the user")
        void throwsWhenImageNotFound() {
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pipelineService.submitPipeline(10L, List.of(5L), 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("10");

            verifyNoInteractions(pipelineRepository, analysisJobService);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when a tool does not exist")
        void throwsWhenToolNotFound() {
            when(dicomImageRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(image));
            when(userRepository.getReferenceById(1L)).thenReturn(user);
            when(pipelineRepository.save(any())).thenAnswer(inv -> {
                Pipeline p = inv.getArgument(0);
                p.setId(99L);
                return p;
            });
            when(toolRepository.findById(5L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pipelineService.submitPipeline(10L, List.of(5L), 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("5");

            verifyNoInteractions(analysisJobService);
        }
    }


    // findLatestForImage()

    @Nested
    @DisplayName("findLatestForImage()")
    class FindLatestForImage {

        @Test
        @DisplayName("returns a PipelineDto when a pipeline exists for the image")
        void returnsPipelineDtoWhenPresent() {
            Pipeline pipeline = buildPipeline(99L);
            PipelineStep step = buildStep(200L, pipeline, 0, tool1, PipelineStatus.RUNNING, null);

            when(pipelineRepository.findFirstByImageIdAndUserIdOrderByCreatedAtDesc(10L, 1L))
                    .thenReturn(Optional.of(pipeline));
            when(pipelineStepRepository.findByPipelineIdOrderByStepOrder(99L))
                    .thenReturn(List.of(step));

            Optional<PipelineDto> result = pipelineService.findLatestForImage(10L, 1L);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(99L);
        }

        @Test
        @DisplayName("returns empty when no pipeline exists for the image")
        void returnsEmptyWhenNoPipelineExists() {
            when(pipelineRepository.findFirstByImageIdAndUserIdOrderByCreatedAtDesc(10L, 1L))
                    .thenReturn(Optional.empty());

            Optional<PipelineDto> result = pipelineService.findLatestForImage(10L, 1L);

            assertThat(result).isEmpty();
            verifyNoInteractions(pipelineStepRepository);
        }
    }


    // onJobCompleted()

    @Nested
    @DisplayName("onJobCompleted()")
    class OnJobCompleted {

        @Test
        @DisplayName("returns early when the completed job is not part of any pipeline")
        void returnsEarlyWhenStepNotFound() {
            when(pipelineStepRepository.findByAnalysisJobId(42L)).thenReturn(Optional.empty());

            pipelineService.onJobCompleted(new AnalysisJobCompletedEvent(42L, 77L));

            verify(pipelineStepRepository, never()).save(any());
            verifyNoInteractions(pipelineRepository, analysisJobService);
        }

        @Test
        @DisplayName("returns early when the corrected image cannot be found")
        void returnsEarlyWhenCorrectedImageMissing() {
            Pipeline pipeline = buildPipeline(99L);
            PipelineStep step = buildStep(200L, pipeline, 0, tool1, PipelineStatus.RUNNING, null);

            when(pipelineStepRepository.findByAnalysisJobId(42L)).thenReturn(Optional.of(step));
            when(dicomImageRepository.findById(77L)).thenReturn(Optional.empty());

            pipelineService.onJobCompleted(new AnalysisJobCompletedEvent(42L, 77L));

            verify(pipelineStepRepository, never()).save(any());
            verifyNoInteractions(pipelineRepository, analysisJobService);
        }

        @Test
        @DisplayName("marks pipeline COMPLETED when the last step finishes")
        void completesPipelineWhenLastStepDone() {
            Pipeline pipeline = buildPipeline(99L);
            PipelineStep step0 = buildStep(200L, pipeline, 0, tool1, PipelineStatus.RUNNING, null);
            DicomImage outputImage = buildOutputImage(77L, "img-set-xyz");

            when(pipelineStepRepository.findByAnalysisJobId(42L)).thenReturn(Optional.of(step0));
            when(dicomImageRepository.findById(77L)).thenReturn(Optional.of(outputImage));
            when(pipelineStepRepository.findByPipelineIdOrderByStepOrder(99L))
                    .thenReturn(List.of(step0));
            when(pipelineStepRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(pipelineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pipelineService.onJobCompleted(new AnalysisJobCompletedEvent(42L, 77L));

            ArgumentCaptor<Pipeline> captor = ArgumentCaptor.forClass(Pipeline.class);
            verify(pipelineRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PipelineStatus.COMPLETED);

            verifyNoInteractions(analysisJobService);
        }

        @Test
        @DisplayName("submits the next step when the output image has an imageSetId")
        void submitsNextStepWhenImageSetIdPresent() {
            Pipeline pipeline = buildPipeline(99L);
            PipelineStep step0 = buildStep(200L, pipeline, 0, tool1, PipelineStatus.RUNNING, null);
            PipelineStep step1 = buildStep(201L, pipeline, 1, tool2, PipelineStatus.PENDING, null);
            DicomImage outputImage = buildOutputImage(77L, "img-set-xyz");

            when(pipelineStepRepository.findByAnalysisJobId(42L)).thenReturn(Optional.of(step0));
            when(dicomImageRepository.findById(77L)).thenReturn(Optional.of(outputImage));
            when(pipelineStepRepository.findByPipelineIdOrderByStepOrder(99L))
                    .thenReturn(List.of(step0, step1));
            when(pipelineStepRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(analysisJobService.submit(77L, 6L, 1L)).thenReturn(buildJob(43L));

            pipelineService.onJobCompleted(new AnalysisJobCompletedEvent(42L, 77L));

            verify(analysisJobService).submit(77L, 6L, 1L);

            ArgumentCaptor<PipelineStep> stepCaptor = ArgumentCaptor.forClass(PipelineStep.class);
            verify(pipelineStepRepository, atLeastOnce()).save(stepCaptor.capture());
            assertThat(stepCaptor.getAllValues())
                    .anyMatch(s -> s.getId().equals(201L) && s.getStatus() == PipelineStatus.RUNNING);
        }

        @Test
        @DisplayName("leaves next step PENDING when the output image has no imageSetId yet")
        void defersNextStepWhenImageSetIdMissing() {
            Pipeline pipeline = buildPipeline(99L);
            PipelineStep step0 = buildStep(200L, pipeline, 0, tool1, PipelineStatus.RUNNING, null);
            PipelineStep step1 = buildStep(201L, pipeline, 1, tool2, PipelineStatus.PENDING, null);
            DicomImage outputImage = buildOutputImage(77L, null);

            when(pipelineStepRepository.findByAnalysisJobId(42L)).thenReturn(Optional.of(step0));
            when(dicomImageRepository.findById(77L)).thenReturn(Optional.of(outputImage));
            when(pipelineStepRepository.findByPipelineIdOrderByStepOrder(99L))
                    .thenReturn(List.of(step0, step1));
            when(pipelineStepRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pipelineService.onJobCompleted(new AnalysisJobCompletedEvent(42L, 77L));

            verifyNoInteractions(analysisJobService);
            assertThat(step1.getStatus()).isEqualTo(PipelineStatus.PENDING);
        }

        @Test
        @DisplayName("marks pipeline FAILED when submitting the next step throws")
        void marksPipelineFailedWhenSubmitThrows() {
            Pipeline pipeline = buildPipeline(99L);
            PipelineStep step0 = buildStep(200L, pipeline, 0, tool1, PipelineStatus.RUNNING, null);
            PipelineStep step1 = buildStep(201L, pipeline, 1, tool2, PipelineStatus.PENDING, null);
            DicomImage outputImage = buildOutputImage(77L, "img-set-xyz");

            when(pipelineStepRepository.findByAnalysisJobId(42L)).thenReturn(Optional.of(step0));
            when(dicomImageRepository.findById(77L)).thenReturn(Optional.of(outputImage));
            when(pipelineStepRepository.findByPipelineIdOrderByStepOrder(99L))
                    .thenReturn(List.of(step0, step1));
            when(pipelineStepRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(analysisJobService.submit(anyLong(), anyLong(), anyLong()))
                    .thenThrow(new RuntimeException("ECS rejected"));
            when(pipelineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pipelineService.onJobCompleted(new AnalysisJobCompletedEvent(42L, 77L));

            ArgumentCaptor<Pipeline> captor = ArgumentCaptor.forClass(Pipeline.class);
            verify(pipelineRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PipelineStatus.FAILED);
        }
    }


    // advanceWaitingPipelines()

    @Nested
    @DisplayName("advanceWaitingPipelines()")
    class AdvanceWaitingPipelines {

        @Test
        @DisplayName("submits the next step when HealthImaging import completes between polling cycles")
        void advancesStepWhenImageSetIdNowPresent() {
            Pipeline pipeline = buildPipeline(99L);
            DicomImage prevOutputStale = buildOutputImage(77L, null);
            DicomImage prevOutputFresh = buildOutputImage(77L, "img-set-xyz");

            PipelineStep step0 = buildStep(200L, pipeline, 0, tool1, PipelineStatus.COMPLETED, prevOutputStale);
            PipelineStep step1 = buildStep(201L, pipeline, 1, tool2, PipelineStatus.PENDING, null);

            when(pipelineRepository.findByStatusIn(List.of(PipelineStatus.RUNNING)))
                    .thenReturn(List.of(pipeline));
            when(pipelineStepRepository.findByPipelineIdOrderByStepOrder(99L))
                    .thenReturn(List.of(step0, step1));
            when(dicomImageRepository.findById(77L)).thenReturn(Optional.of(prevOutputFresh));
            when(analysisJobService.submit(77L, 6L, 1L)).thenReturn(buildJob(43L));
            when(pipelineStepRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            pipelineService.advanceWaitingPipelines();

            verify(analysisJobService).submit(77L, 6L, 1L);
        }

        @Test
        @DisplayName("skips the pipeline when the previous step's output image still has no imageSetId")
        void skipsWhenImageSetIdStillMissing() {
            Pipeline pipeline = buildPipeline(99L);
            DicomImage prevOutput = buildOutputImage(77L, null);

            PipelineStep step0 = buildStep(200L, pipeline, 0, tool1, PipelineStatus.COMPLETED, prevOutput);
            PipelineStep step1 = buildStep(201L, pipeline, 1, tool2, PipelineStatus.PENDING, null);

            when(pipelineRepository.findByStatusIn(List.of(PipelineStatus.RUNNING)))
                    .thenReturn(List.of(pipeline));
            when(pipelineStepRepository.findByPipelineIdOrderByStepOrder(99L))
                    .thenReturn(List.of(step0, step1));
            when(dicomImageRepository.findById(77L)).thenReturn(Optional.of(prevOutput));

            pipelineService.advanceWaitingPipelines();

            verifyNoInteractions(analysisJobService);
        }

        @Test
        @DisplayName("does nothing when no RUNNING pipelines exist")
        void doesNothingWithNoRunningPipelines() {
            when(pipelineRepository.findByStatusIn(List.of(PipelineStatus.RUNNING)))
                    .thenReturn(List.of());

            pipelineService.advanceWaitingPipelines();

            verifyNoInteractions(pipelineStepRepository, analysisJobService);
        }
    }


    // Helpers

    private Pipeline buildPipeline(Long id) {
        Pipeline pipeline = new Pipeline();
        pipeline.setId(id);
        pipeline.setImage(image);
        pipeline.setUser(user);
        pipeline.setStatus(PipelineStatus.RUNNING);
        pipeline.setUpdatedAt(Instant.now());
        return pipeline;
    }

    private PipelineStep buildStep(Long id, Pipeline pipeline, int order, Tool tool,
                                   PipelineStatus status, DicomImage outputImage) {
        PipelineStep step = new PipelineStep();
        step.setId(id);
        step.setPipeline(pipeline);
        step.setStepOrder(order);
        step.setTool(tool);
        step.setStatus(status);
        step.setOutputImage(outputImage);
        step.setUpdatedAt(Instant.now());
        return step;
    }

    private DicomImage buildOutputImage(Long id, String imageSetId) {
        DicomImage img = new DicomImage();
        img.setId(id);
        img.setImageSetId(imageSetId);
        return img;
    }

    private AnalysisJob buildJob(Long id) {
        return AnalysisJob.builder()
                .id(id)
                .image(image)
                .tool(tool1)
                .user(user)
                .status(JobStatus.SUBMITTED)
                .updatedAt(Instant.now())
                .build();
    }
}

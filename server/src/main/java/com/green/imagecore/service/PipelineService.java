package com.green.imagecore.service;

import com.green.imagecore.dto.PipelineDto;
import com.green.imagecore.entities.*;
import com.green.imagecore.events.AnalysisJobCompletedEvent;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.mapper.PipelineMapper;
import com.green.imagecore.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineService {

    private final PipelineRepository        pipelineRepository;
    private final PipelineStepRepository    pipelineStepRepository;
    private final DicomImageRepository      dicomImageRepository;
    private final ToolRepository            toolRepository;
    private final UserRepository            userRepository;
    private final AnalysisJobService        analysisJobService;

    /**
     * Creates a pipeline for the given image, submits step 0 to ECS immediately,
     * and leaves the remaining steps in PENDING state for sequential dispatch.
     */
    @Transactional
    public PipelineDto submitPipeline(Long imageId, List<Long> toolIds, Long userId) {
        DicomImage image = dicomImageRepository.findByIdAndUserId(imageId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found with id: " + imageId));

        Pipeline pipeline = Pipeline.builder()
                .image(image)
                .user(userRepository.getReferenceById(userId))
                .status(PipelineStatus.RUNNING)
                .updatedAt(Instant.now())
                .build();
        pipeline = pipelineRepository.save(pipeline);

        List<PipelineStep> steps = new ArrayList<>();
        for (int i = 0; i < toolIds.size(); i++) {
            Long toolId = toolIds.get(i);
            Tool tool = toolRepository.findById(toolId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tool not found with id: " + toolId));
            PipelineStep step = PipelineStep.builder()
                    .pipeline(pipeline)
                    .stepOrder(i)
                    .tool(tool)
                    .status(PipelineStatus.PENDING)
                    .updatedAt(Instant.now())
                    .build();
            steps.add(pipelineStepRepository.save(step));
        }

        // Submit step 0 against the original image immediately
        AnalysisJob step0Job = analysisJobService.submit(imageId, toolIds.get(0), userId);
        steps.get(0).setAnalysisJob(step0Job);
        steps.get(0).setStatus(PipelineStatus.RUNNING);
        steps.get(0).setUpdatedAt(Instant.now());
        pipelineStepRepository.save(steps.get(0));

        return PipelineMapper.toDto(pipeline, steps);
    }

    /**
     * Returns the most recent pipeline for the given image owned by the user, if any.
     */
    @Transactional(readOnly = true)
    public Optional<PipelineDto> findLatestForImage(Long imageId, Long userId) {
        return pipelineRepository
                .findFirstByImageIdAndUserIdOrderByCreatedAtDesc(imageId, userId)
                .map(p -> {
                    List<PipelineStep> steps = pipelineStepRepository
                            .findByPipelineIdOrderByStepOrder(p.getId());
                    return PipelineMapper.toDto(p, steps);
                });
    }

    /**
     * Runs after the AnalysisJobService transaction commits so that the corrected DicomImage
     * is visible in the DB before we attempt to advance the pipeline.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void onJobCompleted(AnalysisJobCompletedEvent event) {
        PipelineStep step = pipelineStepRepository
                .findByAnalysisJobId(event.jobId())
                .orElse(null);
        if (step == null) {
            return; // standalone job, not part of a pipeline
        }

        DicomImage outputImage = dicomImageRepository.findById(event.correctedImageId())
                .orElse(null);
        if (outputImage == null) {
            log.warn("Pipeline step {} completed but corrected image {} not found",
                    step.getId(), event.correctedImageId());
            return;
        }

        step.setOutputImage(outputImage);
        step.setStatus(PipelineStatus.COMPLETED);
        step.setUpdatedAt(Instant.now());
        pipelineStepRepository.save(step);

        List<PipelineStep> steps = pipelineStepRepository
                .findByPipelineIdOrderByStepOrder(step.getPipeline().getId());
        int nextOrder = step.getStepOrder() + 1;

        if (nextOrder >= steps.size()) {
            completePipeline(step.getPipeline());
            return;
        }

        tryAdvanceStep(steps.get(nextOrder), outputImage);
    }

    /**
     * Fallback poller: advances any RUNNING pipeline whose next step is still PENDING
     * because the HealthImaging import wasn't finished when the completion event fired.
     */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void advanceWaitingPipelines() {
        List<Pipeline> running = pipelineRepository.findByStatusIn(List.of(PipelineStatus.RUNNING));
        for (Pipeline pipeline : running) {
            try {
                advancePipelineIfReady(pipeline);
            } catch (Exception e) {
                log.error("Error advancing pipeline {}: {}", pipeline.getId(), e.getMessage(), e);
            }
        }
    }

    private void advancePipelineIfReady(Pipeline pipeline) {
        List<PipelineStep> steps = pipelineStepRepository
                .findByPipelineIdOrderByStepOrder(pipeline.getId());

        PipelineStep pending = steps.stream()
                .filter(s -> s.getStatus() == PipelineStatus.PENDING)
                .findFirst()
                .orElse(null);
        if (pending == null) return;

        int prevOrder = pending.getStepOrder() - 1;
        if (prevOrder < 0) return;

        PipelineStep prev = steps.get(prevOrder);
        if (prev.getOutputImage() == null) return;

        // Re-fetch to get the latest imageSetId written by DicomImportJobListener
        DicomImage outputImage = dicomImageRepository
                .findById(prev.getOutputImage().getId())
                .orElse(null);
        if (outputImage == null || outputImage.getImageSetId() == null) return;

        tryAdvanceStep(pending, outputImage);
    }

    private void tryAdvanceStep(PipelineStep nextStep, DicomImage outputImage) {
        if (nextStep.getStatus() != PipelineStatus.PENDING) return;

        // HealthImaging import must be complete so submit() can run against the image set
        if (outputImage.getImageSetId() == null) {
            log.debug("Pipeline step {} waiting for HealthImaging import of image {}",
                    nextStep.getId(), outputImage.getId());
            return;
        }

        Pipeline pipeline = nextStep.getPipeline();
        Long userId = pipeline.getUser().getId();

        try {
            AnalysisJob job = analysisJobService.submit(
                    outputImage.getId(), nextStep.getTool().getToolId(), userId);
            nextStep.setAnalysisJob(job);
            nextStep.setStatus(PipelineStatus.RUNNING);
            nextStep.setUpdatedAt(Instant.now());
            pipelineStepRepository.save(nextStep);
            log.info("Pipeline {} advanced to step {}", pipeline.getId(), nextStep.getStepOrder());
        } catch (Exception e) {
            log.error("Failed to advance pipeline {} step {}: {}",
                    pipeline.getId(), nextStep.getStepOrder(), e.getMessage());
            pipeline.setStatus(PipelineStatus.FAILED);
            pipeline.setUpdatedAt(Instant.now());
            pipelineRepository.save(pipeline);
        }
    }

    private void completePipeline(Pipeline pipeline) {
        pipeline.setStatus(PipelineStatus.COMPLETED);
        pipeline.setUpdatedAt(Instant.now());
        pipelineRepository.save(pipeline);
        log.info("Pipeline {} completed", pipeline.getId());
    }
}

package com.green.imagecore.events;

/**
 * Published by {@link com.green.imagecore.service.AnalysisJobService} after a job reaches
 * COMPLETED status and its corrected DicomImage has been persisted.
 * {@link com.green.imagecore.service.PipelineService} listens for this to advance
 * multi-step pipelines to the next step.
 */
public record AnalysisJobCompletedEvent(Long jobId, Long correctedImageId) {}

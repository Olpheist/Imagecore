package com.green.imagecore.events;

/**
 * Published by {@link com.green.imagecore.service.DicomUploadService} immediately after a
 * HealthImaging import job is submitted. The async listener picks this up and polls the job
 * status until it reaches a terminal state, then triggers metadata population.
 */
public record DicomImportSubmittedEvent(Long imageId) {}

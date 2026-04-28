package com.green.imagecore.events;

import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.ImportStatus;
import com.green.imagecore.repositories.DicomImageRepository;
import com.green.imagecore.service.HealthImagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens for {@link DicomImportSubmittedEvent} and polls the HealthImaging job status
 * in the background until the job reaches a terminal state (COMPLETED or FAILED).
 *
 * When the job completes, {@code syncImportStatus} automatically calls
 * {@code populateMetadata}, which extracts all SOP instance UIDs, frame IDs,
 * and DICOM tags from the imageSet metadata — making it immediately available to the viewer.
 *
 * Runs on the {@code dicomImportListenerExecutor} thread pool so it never blocks the
 * request thread and doesn't interfere with S3 upload concurrency.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DicomImportJobListener {

    /** Maximum number of status polls before giving up. Default: 40 × 15 s = 10 minutes. */
    @Value("${app.dicom.import.max-poll-attempts:40}")
    private int maxPollAttempts;

    /** Milliseconds to wait between each status poll. */
    @Value("${app.dicom.import.poll-interval-ms:15000}")
    private long pollIntervalMs;

    private final DicomImageRepository dicomImageRepository;
    private final HealthImagingService  healthImagingService;

    /**
     * Triggered after each upload. Runs asynchronously so the HTTP response returns
     * immediately to the client while we wait for HealthImaging in the background.
     */
    @EventListener
    @Async("dicomImportListenerExecutor")
    public void onImportSubmitted(DicomImportSubmittedEvent event) {
        Long imageId = event.imageId();
        log.info("Import listener started for image {}", imageId);

        for (int attempt = 1; attempt <= maxPollAttempts; attempt++) {
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Import listener interrupted for image {}", imageId);
                return;
            }

            DicomImage image = dicomImageRepository.findById(imageId).orElse(null);
            if (image == null) {
                log.warn("Image {} no longer exists — stopping import listener", imageId);
                return;
            }

            try {
                image = healthImagingService.syncImportStatus(image);
            } catch (Exception e) {
                log.warn("Import poll attempt {}/{} failed for image {}: {}",
                        attempt, maxPollAttempts, imageId, e.getMessage());
                continue;
            }

            ImportStatus status = image.getImportStatus();
            log.debug("Import poll {}/{} for image {}: status={}", attempt, maxPollAttempts, imageId, status);

            if (status == ImportStatus.COMPLETED) {
                log.info("Import completed for image {} after {} poll(s) — metadata populated", imageId, attempt);
                return;
            }
            if (status == ImportStatus.FAILED) {
                log.warn("Import failed for image {} after {} poll(s)", imageId, attempt);
                return;
            }
        }

        log.warn("Import for image {} did not complete within {} minutes — marking FAILED",
                imageId, maxPollAttempts * pollIntervalMs / 60_000);
        DicomImage image = dicomImageRepository.findById(imageId).orElse(null);
        if (image != null
                && image.getImportStatus() != ImportStatus.COMPLETED
                && image.getImportStatus() != ImportStatus.FAILED) {
            image.setImportStatus(ImportStatus.FAILED);
            dicomImageRepository.save(image);
        }
    }
}

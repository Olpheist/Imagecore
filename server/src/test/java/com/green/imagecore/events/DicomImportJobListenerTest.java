package com.green.imagecore.events;

import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.ImportStatus;
import com.green.imagecore.repositories.DicomImageRepository;
import com.green.imagecore.service.HealthImagingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DicomImportJobListenerTest {

    @Mock
    private DicomImageRepository dicomImageRepository;

    @Mock
    private HealthImagingService healthImagingService;

    @InjectMocks
    private DicomImportJobListener listener;

    @BeforeEach
    void setUp() {
        // Skip the 15-second sleep and limit to a small number of iterations so tests run instantly
        ReflectionTestUtils.setField(listener, "pollIntervalMs", 0L);
        ReflectionTestUtils.setField(listener, "maxPollAttempts", 5);
    }


    // returns early when image is deleted mid-import

    @Test
    void onImportSubmitted_ReturnsEarly_WhenImageNoLongerExists() {
        when(dicomImageRepository.findById(1L)).thenReturn(Optional.empty());

        listener.onImportSubmitted(new DicomImportSubmittedEvent(1L));

        verify(dicomImageRepository).findById(1L);
        verifyNoInteractions(healthImagingService);
    }


    // terminal status COMPLETED

    @Test
    void onImportSubmitted_StopsPolling_WhenStatusBecomesCompleted() {
        DicomImage inProgress = buildImage(1L, ImportStatus.IN_PROGRESS);
        DicomImage completed  = buildImage(1L, ImportStatus.COMPLETED);

        when(dicomImageRepository.findById(1L)).thenReturn(Optional.of(inProgress));
        when(healthImagingService.syncImportStatus(any())).thenReturn(completed);

        listener.onImportSubmitted(new DicomImportSubmittedEvent(1L));

        // Stops after the first poll that returns COMPLETED
        verify(healthImagingService, times(1)).syncImportStatus(any());
    }


    // terminal status FAILED

    @Test
    void onImportSubmitted_StopsPolling_WhenStatusBecomesFailed() {
        DicomImage inProgress = buildImage(1L, ImportStatus.IN_PROGRESS);
        DicomImage failed     = buildImage(1L, ImportStatus.FAILED);

        when(dicomImageRepository.findById(1L)).thenReturn(Optional.of(inProgress));
        when(healthImagingService.syncImportStatus(any())).thenReturn(failed);

        listener.onImportSubmitted(new DicomImportSubmittedEvent(1L));

        verify(healthImagingService, times(1)).syncImportStatus(any());
    }


    // transient errors don't abort the loop

    @Test
    void onImportSubmitted_ContinuesPolling_AfterTransientSyncException() {
        DicomImage inProgress = buildImage(1L, ImportStatus.IN_PROGRESS);
        DicomImage completed  = buildImage(1L, ImportStatus.COMPLETED);

        when(dicomImageRepository.findById(1L)).thenReturn(Optional.of(inProgress));
        when(healthImagingService.syncImportStatus(any()))
                .thenThrow(new RuntimeException("transient AWS error"))
                .thenReturn(completed);

        listener.onImportSubmitted(new DicomImportSubmittedEvent(1L));

        // Poll 1 throws, poll 2 returns COMPLETED — exactly 2 calls
        verify(healthImagingService, times(2)).syncImportStatus(any());
    }


    // max-attempts guard

    @Test
    void onImportSubmitted_GivesUp_AfterMaxAttempts_WhenNeverReachesTerminalState() {
        DicomImage inProgress = buildImage(1L, ImportStatus.IN_PROGRESS);

        when(dicomImageRepository.findById(1L)).thenReturn(Optional.of(inProgress));
        when(healthImagingService.syncImportStatus(any())).thenReturn(inProgress);

        listener.onImportSubmitted(new DicomImportSubmittedEvent(1L));

        // maxPollAttempts is set to 5 in setUp — should stop after exactly 5 polls
        verify(healthImagingService, times(5)).syncImportStatus(any());
    }


    // InterruptedException handling

    @Test
    void onImportSubmitted_StopsPolling_WhenInterrupted() throws InterruptedException {
        // Run the listener in a real thread so we can interrupt it mid-sleep
        // Set pollIntervalMs to a small but non-zero value so Thread.sleep is actually called
        ReflectionTestUtils.setField(listener, "pollIntervalMs", 50L);
        ReflectionTestUtils.setField(listener, "maxPollAttempts", 10);

        DicomImage inProgress = buildImage(1L, ImportStatus.IN_PROGRESS);
        // Lenient: thread may be interrupted before these stubs are reached
        lenient().when(dicomImageRepository.findById(1L)).thenReturn(Optional.of(inProgress));
        lenient().when(healthImagingService.syncImportStatus(any())).thenReturn(inProgress);

        Thread listenerThread = new Thread(() ->
                listener.onImportSubmitted(new DicomImportSubmittedEvent(1L)));
        listenerThread.start();
        // Give the thread a moment to enter its first sleep, then interrupt it
        Thread.sleep(10);
        listenerThread.interrupt();
        listenerThread.join(1000); // should finish well within 1 second after interrupt

        // The thread should have exited cleanly (not still running)
        assertFalse(listenerThread.isAlive(), "Listener thread should have exited after interrupt");
    }

    // helper

    private DicomImage buildImage(Long id, ImportStatus status) {
        DicomImage img = new DicomImage();
        img.setId(id);
        img.setImportStatus(status);
        img.setHealthImagingJobId("job-" + id);
        return img;
    }
}

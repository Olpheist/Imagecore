package com.green.imagecore.service;

import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.ImportStatus;
import com.green.imagecore.repositories.DicomImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.medicalimaging.MedicalImagingClient;
import software.amazon.awssdk.services.medicalimaging.model.DICOMImportJobProperties;
import software.amazon.awssdk.services.medicalimaging.model.GetDicomImportJobRequest;
import software.amazon.awssdk.services.medicalimaging.model.GetDicomImportJobResponse;
import software.amazon.awssdk.services.medicalimaging.model.JobStatus;
import software.amazon.awssdk.services.medicalimaging.model.StartDicomImportJobRequest;
import software.amazon.awssdk.services.medicalimaging.model.StartDicomImportJobResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthImagingServiceTest {

    @Mock private MedicalImagingClient medicalImagingClient;
    @Mock private S3Client s3Client;
    @Mock private DicomImageRepository dicomImageRepository;

    @InjectMocks
    private HealthImagingService healthImagingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(healthImagingService, "datastoreId", "test-datastore-id");
        ReflectionTestUtils.setField(healthImagingService, "importRoleArn", "arn:aws:iam::000000000000:role/test-import-role");
    }

    // tests for startImportJob()

    @Test
    void startImportJob_returnsJobIdFromResponse() {
        StartDicomImportJobResponse response = mock(StartDicomImportJobResponse.class);
        when(response.jobId()).thenReturn("job-123");
        when(medicalImagingClient.startDICOMImportJob(any(StartDicomImportJobRequest.class))).thenReturn(response);

        String jobId = healthImagingService.startImportJob(
                "s3://bucket/dicom/1/uuid/",
                "s3://bucket/health-imaging-output/1/uuid/"
        );

        assertThat(jobId).isEqualTo("job-123");
    }

    @Test
    void startImportJob_buildsRequestWithConfiguredDatastoreAndRole() {
        StartDicomImportJobResponse response = mock(StartDicomImportJobResponse.class);
        when(response.jobId()).thenReturn("job-123");
        when(medicalImagingClient.startDICOMImportJob(any(StartDicomImportJobRequest.class))).thenReturn(response);

        healthImagingService.startImportJob(
                "s3://bucket/dicom/1/uuid/",
                "s3://bucket/health-imaging-output/1/uuid/"
        );

        ArgumentCaptor<StartDicomImportJobRequest> captor = ArgumentCaptor.forClass(StartDicomImportJobRequest.class);
        verify(medicalImagingClient).startDICOMImportJob(captor.capture());
        StartDicomImportJobRequest captured = captor.getValue();
        assertThat(captured.datastoreId()).isEqualTo("test-datastore-id");
        assertThat(captured.dataAccessRoleArn()).isEqualTo("arn:aws:iam::000000000000:role/test-import-role");
        assertThat(captured.inputS3Uri()).isEqualTo("s3://bucket/dicom/1/uuid/");
        assertThat(captured.outputS3Uri()).isEqualTo("s3://bucket/health-imaging-output/1/uuid/");
    }

    // if the image is already COMPLETED or FAILED, should not call AWS again

    @Test
    void syncImportStatus_skipsAwsCallWhenAlreadyCompleted() {
        DicomImage image = DicomImage.builder()
                .importStatus(ImportStatus.COMPLETED)
                .build();

        DicomImage result = healthImagingService.syncImportStatus(image);

        assertThat(result).isSameAs(image);
        verifyNoInteractions(medicalImagingClient, dicomImageRepository);
    }

    @Test
    void syncImportStatus_skipsAwsCallWhenAlreadyFailed() {
        DicomImage image = DicomImage.builder()
                .importStatus(ImportStatus.FAILED)
                .build();

        DicomImage result = healthImagingService.syncImportStatus(image);

        assertThat(result).isSameAs(image);
        verifyNoInteractions(medicalImagingClient, dicomImageRepository);
    }

    // tests for each import status that HealthImaging can return

    @Test
    void syncImportStatus_setsSubmittedWhenJobIsSubmitted() {
        DicomImage image = buildImageWithJobId("job-123", ImportStatus.PENDING);
        stubGetImportJob("job-123", JobStatus.SUBMITTED, null);
        when(dicomImageRepository.save(image)).thenReturn(image);

        DicomImage result = healthImagingService.syncImportStatus(image);

        assertThat(result.getImportStatus()).isEqualTo(ImportStatus.SUBMITTED);
        verify(dicomImageRepository).save(image);
    }

    @Test
    void syncImportStatus_setsInProgressWhenJobIsInProgress() {
        DicomImage image = buildImageWithJobId("job-123", ImportStatus.SUBMITTED);
        stubGetImportJob("job-123", JobStatus.IN_PROGRESS, null);
        when(dicomImageRepository.save(image)).thenReturn(image);

        DicomImage result = healthImagingService.syncImportStatus(image);

        assertThat(result.getImportStatus()).isEqualTo(ImportStatus.IN_PROGRESS);
        verify(dicomImageRepository).save(image);
    }

    @Test
    void syncImportStatus_setsFailedWhenJobFails() {
        DicomImage image = buildImageWithJobId("job-123", ImportStatus.IN_PROGRESS);
        stubGetImportJob("job-123", JobStatus.FAILED, null);
        when(dicomImageRepository.save(image)).thenReturn(image);

        DicomImage result = healthImagingService.syncImportStatus(image);

        assertThat(result.getImportStatus()).isEqualTo(ImportStatus.FAILED);
        verify(dicomImageRepository).save(image);
    }

    // when the job completes, read the imageSetId out of the S3 manifest HealthImaging wrote

    @Test
    void syncImportStatus_extractsImageSetIdFromManifestWhenCompleted() {
        DicomImage image = buildImageWithJobId("job-123", ImportStatus.IN_PROGRESS);
        stubGetImportJob("job-123", JobStatus.COMPLETED, "s3://bucket/health-imaging-output/1/uuid/");
        stubS3Manifest("{\"imageSetsSummary\":[{\"imageSetId\":\"image-set-abc\"}]}");
        when(dicomImageRepository.save(image)).thenReturn(image);

        DicomImage result = healthImagingService.syncImportStatus(image);

        assertThat(result.getImportStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(result.getImageSetId()).isEqualTo("image-set-abc");
        verify(dicomImageRepository).save(image);
    }

    @Test
    void syncImportStatus_setsCompletedWithNullImageSetIdWhenS3ManifestUnreadable() {
        DicomImage image = buildImageWithJobId("job-123", ImportStatus.IN_PROGRESS);
        stubGetImportJob("job-123", JobStatus.COMPLETED, "s3://bucket/health-imaging-output/1/uuid/");
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(new RuntimeException("S3 read failed"));
        when(dicomImageRepository.save(image)).thenReturn(image);

        DicomImage result = healthImagingService.syncImportStatus(image);

        assertThat(result.getImportStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(result.getImageSetId()).isNull();
    }

    @Test
    void syncImportStatus_setsCompletedWithNullImageSetIdWhenManifestJsonMalformed() {
        DicomImage image = buildImageWithJobId("job-123", ImportStatus.IN_PROGRESS);
        stubGetImportJob("job-123", JobStatus.COMPLETED, "s3://bucket/health-imaging-output/1/uuid/");
        stubS3Manifest("not-valid-json");
        when(dicomImageRepository.save(image)).thenReturn(image);

        DicomImage result = healthImagingService.syncImportStatus(image);

        assertThat(result.getImportStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(result.getImageSetId()).isNull();
    }

    // helper methods to reduce repetition in the tests above

    private DicomImage buildImageWithJobId(String jobId, ImportStatus status) {
        return DicomImage.builder()
                .healthImagingJobId(jobId)
                .importStatus(status)
                .build();
    }

    private void stubGetImportJob(String jobId, JobStatus status, String outputS3Uri) {
        DICOMImportJobProperties props = mock(DICOMImportJobProperties.class);
        when(props.jobStatus()).thenReturn(status);
        if (outputS3Uri != null) {
            when(props.outputS3Uri()).thenReturn(outputS3Uri);
        }
        GetDicomImportJobResponse response = mock(GetDicomImportJobResponse.class);
        when(response.jobProperties()).thenReturn(props);
        when(medicalImagingClient.getDICOMImportJob(any(GetDicomImportJobRequest.class))).thenReturn(response);
    }

    @SuppressWarnings("unchecked")
    private void stubS3Manifest(String json) {
        ResponseBytes<GetObjectResponse> responseBytes = mock(ResponseBytes.class);
        when(responseBytes.asByteArray()).thenReturn(json.getBytes());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);
    }
}

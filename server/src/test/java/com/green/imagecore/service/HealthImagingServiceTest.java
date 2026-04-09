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
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.medicalimaging.MedicalImagingClient;
import software.amazon.awssdk.services.medicalimaging.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
        // A fully-resolved COMPLETED record (imageSetId + all metadata fields populated)
        // should short-circuit without calling AWS or saving to the DB.
        DicomImage image = DicomImage.builder()
                .importStatus(ImportStatus.COMPLETED)
                .imageSetId("image-set-abc")
                .seriesInstanceUid("1.2.3.4.5.1")
                .sopInstanceUid("1.2.3.4.5.1.1")
                .sopInstanceUids("[\"1.2.3.4.5.1.1\"]")
                .sopFrameMap("{\"1.2.3.4.5.1.1\":\"frame-001\"}")
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
        // populateMetadata is called on COMPLETED — stub the metadata call to avoid NPE
        when(medicalImagingClient.getImageSetMetadata(any(GetImageSetMetadataRequest.class)))
                .thenThrow(new RuntimeException("metadata not available in test"));
        when(dicomImageRepository.save(image)).thenReturn(image);

        DicomImage result = healthImagingService.syncImportStatus(image);

        assertThat(result.getImportStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(result.getImageSetId()).isEqualTo("image-set-abc");
        verify(dicomImageRepository).save(image);
    }

    @Test
    void syncImportStatus_setsFailedWhenJobCompletedButManifestUnreadable() {
        // If the S3 manifest can't be read, no imageSetId is available — treat as FAILED
        // so the catalog shows an error state rather than a misleading COMPLETED with no data.
        DicomImage image = buildImageWithJobId("job-123", ImportStatus.IN_PROGRESS);
        stubGetImportJob("job-123", JobStatus.COMPLETED, "s3://bucket/health-imaging-output/1/uuid/");
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(new RuntimeException("S3 read failed"));
        when(dicomImageRepository.save(image)).thenReturn(image);

        DicomImage result = healthImagingService.syncImportStatus(image);

        assertThat(result.getImportStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(result.getImageSetId()).isNull();
    }

    @Test
    void syncImportStatus_setsFailedWhenJobCompletedButManifestJsonMalformed() {
        // Malformed manifest → no imageSetId → FAILED (same reasoning as unreadable manifest)
        DicomImage image = buildImageWithJobId("job-123", ImportStatus.IN_PROGRESS);
        stubGetImportJob("job-123", JobStatus.COMPLETED, "s3://bucket/health-imaging-output/1/uuid/");
        stubS3Manifest("not-valid-json");
        when(dicomImageRepository.save(image)).thenReturn(image);

        DicomImage result = healthImagingService.syncImportStatus(image);

        assertThat(result.getImportStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(result.getImageSetId()).isNull();
    }

    // tests for populateMetadata()

    @Test
    void populateMetadata_extractsAllFieldsFromMetadataResponse() {
        DicomImage image = DicomImage.builder()
                .imageSetId("image-set-abc")
                .importStatus(ImportStatus.COMPLETED)
                .build();

        // HealthImaging metadata uses keyword names (not hex tags) as JSON keys.
        // Two SOP instances → frameCount = 2 (one frame ID stored per instance).
        String metadataJson = """
                {
                  "DatastoreID": "test-datastore-id",
                  "ImageSetID": "image-set-abc",
                  "Patient": {
                    "DICOM": {
                      "PatientID": "PT-001"
                    }
                  },
                  "Study": {
                    "DICOM": {
                      "StudyInstanceUID": "1.2.3.4.5",
                      "StudyDescription": "Brain MRI",
                      "StudyDate": "20260301",
                      "ReferringPhysicianName": "Dr. Smith"
                    },
                    "Series": {
                      "1.2.3.4.5.1": {
                        "DICOM": {
                          "SeriesDescription": "T1 MPRAGE",
                          "Modality": "MR",
                          "BodyPartExamined": "Brain"
                        },
                        "Instances": {
                          "1.2.3.4.5.1.1": {
                            "DICOM": { "InstanceNumber": "1" },
                            "DICOMVRs": {},
                            "ImageFrames": [{ "ID": "frame-001" }]
                          },
                          "1.2.3.4.5.1.2": {
                            "DICOM": { "InstanceNumber": "2" },
                            "DICOMVRs": {},
                            "ImageFrames": [{ "ID": "frame-002" }]
                          }
                        }
                      }
                    }
                  }
                }
                """;
        stubGetImageSetMetadata(metadataJson);

        healthImagingService.populateMetadata(image);

        assertThat(image.getPatientId()).isEqualTo("PT-001");
        assertThat(image.getStudyInstanceUid()).isEqualTo("1.2.3.4.5");
        assertThat(image.getStudyDescription()).isEqualTo("Brain MRI");
        assertThat(image.getStudyDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(image.getPhysician()).isEqualTo("Dr. Smith");
        assertThat(image.getSeriesInstanceUid()).isEqualTo("1.2.3.4.5.1");
        assertThat(image.getSeriesDescription()).isEqualTo("T1 MPRAGE");
        assertThat(image.getModality()).isEqualTo("MR");
        assertThat(image.getBodyPart()).isEqualTo("Brain");
        assertThat(image.getSopInstanceUid()).isEqualTo("1.2.3.4.5.1.1");
        assertThat(image.getImageFrameId()).isEqualTo("frame-001");
        assertThat(image.getFrameCount()).isEqualTo(2);
    }

    @Test
    void populateMetadata_handlesErrorGracefully() {
        DicomImage image = DicomImage.builder()
                .imageSetId("image-set-abc")
                .importStatus(ImportStatus.COMPLETED)
                .build();

        when(medicalImagingClient.getImageSetMetadata(any(GetImageSetMetadataRequest.class)))
                .thenThrow(new RuntimeException("AWS error"));

        // Should not throw
        healthImagingService.populateMetadata(image);

        // Fields remain null
        assertThat(image.getStudyInstanceUid()).isNull();
    }

    // tests for streamImageFrame()

    @Test
    void streamImageFrame_callsGetImageFrameWithCorrectParams() throws IOException {
        ResponseInputStream<GetImageFrameResponse> mockResponse =
                new ResponseInputStream<>(mock(GetImageFrameResponse.class),
                        new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(medicalImagingClient.getImageFrame(any(GetImageFrameRequest.class)))
                .thenReturn(mockResponse);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        healthImagingService.streamImageFrame("image-set-abc", "frame-001", out);

        ArgumentCaptor<GetImageFrameRequest> captor = ArgumentCaptor.forClass(GetImageFrameRequest.class);
        verify(medicalImagingClient).getImageFrame(captor.capture());
        assertThat(captor.getValue().datastoreId()).isEqualTo("test-datastore-id");
        assertThat(captor.getValue().imageSetId()).isEqualTo("image-set-abc");
        assertThat(captor.getValue().imageFrameInformation().imageFrameId()).isEqualTo("frame-001");
        assertThat(out.toByteArray()).containsExactly(1, 2, 3);
    }

    // syncImportStatus — re-populate metadata path

    @Test
    void syncImportStatus_repopulatesMetadata_WhenCompletedWithIncompleteMetadata() {
        // COMPLETED + imageSetId present, but seriesInstanceUid is null → metadataComplete = false
        DicomImage image = DicomImage.builder()
                .importStatus(ImportStatus.COMPLETED)
                .imageSetId("image-set-abc")
                .build();

        when(medicalImagingClient.getImageSetMetadata(any(GetImageSetMetadataRequest.class)))
                .thenThrow(new RuntimeException("metadata unavailable in test"));
        when(dicomImageRepository.save(image)).thenReturn(image);

        DicomImage result = healthImagingService.syncImportStatus(image);

        // Should call populateMetadata (which hits getImageSetMetadata), then save
        verify(medicalImagingClient).getImageSetMetadata(any(GetImageSetMetadataRequest.class));
        verify(dicomImageRepository).save(image);
        // No getDICOMImportJob call — skipped because imageSetId already known
        verify(medicalImagingClient, never()).getDICOMImportJob(any(GetDicomImportJobRequest.class));
        assertThat(result).isSameAs(image);
    }

    // extractImageSetId — alternate manifest structures (tested via syncImportStatus)

    @Test
    void syncImportStatus_extractsImageSetIdFromNestedManifestStructure() {
        // Structure 2: { "jobSummary": { "imageSetsSummary": [{ "imageSetId": "..." }] } }
        DicomImage image = buildImageWithJobId("job-123", ImportStatus.IN_PROGRESS);
        stubGetImportJob("job-123", JobStatus.COMPLETED, "s3://bucket/health-imaging-output/1/uuid/");
        stubS3Manifest("{\"jobSummary\":{\"imageSetsSummary\":[{\"imageSetId\":\"nested-imgset\"}]}}");
        when(medicalImagingClient.getImageSetMetadata(any(GetImageSetMetadataRequest.class)))
                .thenThrow(new RuntimeException("metadata not available in test"));
        when(dicomImageRepository.save(image)).thenReturn(image);

        DicomImage result = healthImagingService.syncImportStatus(image);

        assertThat(result.getImageSetId()).isEqualTo("nested-imgset");
        assertThat(result.getImportStatus()).isEqualTo(ImportStatus.COMPLETED);
    }

    @Test
    void syncImportStatus_extractsImageSetIdFromImageSetsManifestStructure() {
        // Structure 3: { "imageSets": [{ "imageSetId": "..." }] }
        DicomImage image = buildImageWithJobId("job-123", ImportStatus.IN_PROGRESS);
        stubGetImportJob("job-123", JobStatus.COMPLETED, "s3://bucket/health-imaging-output/1/uuid/");
        stubS3Manifest("{\"imageSets\":[{\"imageSetId\":\"flat-imgset\"}]}");
        when(medicalImagingClient.getImageSetMetadata(any(GetImageSetMetadataRequest.class)))
                .thenThrow(new RuntimeException("metadata not available in test"));
        when(dicomImageRepository.save(image)).thenReturn(image);

        DicomImage result = healthImagingService.syncImportStatus(image);

        assertThat(result.getImageSetId()).isEqualTo("flat-imgset");
        assertThat(result.getImportStatus()).isEqualTo(ImportStatus.COMPLETED);
    }

    // getInstanceDicomJson

    @Test
    void getInstanceDicomJson_returnsFormattedDicomJson_ForFoundInstance() throws IOException {
        DicomImage image = DicomImage.builder()
                .imageSetId("image-set-abc")
                .importStatus(ImportStatus.COMPLETED)
                .build();

        String metadataJson = """
                {
                  "Study": {
                    "Series": {
                      "1.2.3.4.5.1": {
                        "DICOM": { "SeriesDescription": "T1 MPRAGE", "Modality": "MR" },
                        "Instances": {
                          "1.2.3.4.5.1.1": {
                            "DICOM": { "InstanceNumber": "1" },
                            "ImageFrames": [{ "ID": "frame-001" }]
                          }
                        }
                      }
                    }
                  }
                }
                """;
        stubGetImageSetMetadata(metadataJson);

        List<Map<String, Object>> result = healthImagingService.getInstanceDicomJson(image, "1.2.3.4.5.1.1");

        assertThat(result).hasSize(1);
        // TransferSyntaxUID should be injected automatically by the method
        assertThat(result.get(0)).containsKey("00020010");
    }

    @Test
    void getInstanceDicomJson_returnsEmptyList_WhenSopNotFoundInMetadata() throws IOException {
        DicomImage image = DicomImage.builder()
                .imageSetId("image-set-abc")
                .importStatus(ImportStatus.COMPLETED)
                .build();

        String metadataJson = """
                {
                  "Study": {
                    "Series": {
                      "1.2.3.4.5.1": {
                        "DICOM": {},
                        "Instances": {
                          "1.2.3.4.5.1.1": { "DICOM": {} }
                        }
                      }
                    }
                  }
                }
                """;
        stubGetImageSetMetadata(metadataJson);

        List<Map<String, Object>> result = healthImagingService.getInstanceDicomJson(image, "9.9.9.NOT-EXIST");

        assertThat(result).isEmpty();
    }

    // getAllInstancesDicomJson

    @Test
    void getAllInstancesDicomJson_returnsAllInstancesKeyedBySopUid() throws IOException {
        String metadataJson = """
                {
                  "Study": {
                    "Series": {
                      "1.2.3.4.5.1": {
                        "DICOM": { "Modality": "MR" },
                        "Instances": {
                          "1.2.3.4.5.1.1": { "DICOM": { "InstanceNumber": "1" } },
                          "1.2.3.4.5.1.2": { "DICOM": { "InstanceNumber": "2" } }
                        }
                      }
                    }
                  }
                }
                """;
        stubGetImageSetMetadata(metadataJson);

        Map<String, List<Map<String, Object>>> result =
                healthImagingService.getAllInstancesDicomJson("image-set-abc");

        assertThat(result).hasSize(2);
        assertThat(result).containsKey("1.2.3.4.5.1.1");
        assertThat(result).containsKey("1.2.3.4.5.1.2");
        // Each value is a one-element list per WADO-RS spec
        assertThat(result.get("1.2.3.4.5.1.1")).hasSize(1);
        // TransferSyntaxUID injected into every instance
        assertThat(result.get("1.2.3.4.5.1.1").get(0)).containsKey("00020010");
    }

    // convertToDicomJson — numeric VR branches (DS float, US integer, float array)

    @Test
    void getAllInstancesDicomJson_handlesNumericVrsInMetadata() throws IOException {
        // PatientSize → DS (float string), Rows → US (integer string)
        String metadataJson = """
                {
                  "Study": {
                    "Series": {
                      "1.2.3.4.5.1": {
                        "DICOM": { "Modality": "MR" },
                        "Instances": {
                          "1.2.3.4.5.1.1": {
                            "DICOM": {
                              "Rows": "512",
                              "PatientSize": "1.75"
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;
        stubGetImageSetMetadata(metadataJson);

        Map<String, List<Map<String, Object>>> result =
                healthImagingService.getAllInstancesDicomJson("image-set-abc");

        assertThat(result).containsKey("1.2.3.4.5.1.1");
        Map<String, Object> dicom = result.get("1.2.3.4.5.1.1").get(0);
        // Rows (US) → integer in Value array
        @SuppressWarnings("unchecked")
        List<Integer> rows = (List<Integer>) ((Map<String, Object>) dicom.get("00280010")).get("Value");
        assertThat(rows).containsExactly(512);
        // PatientSize (DS) → double in Value array
        @SuppressWarnings("unchecked")
        List<Double> size = (List<Double>) ((Map<String, Object>) dicom.get("00101020")).get("Value");
        assertThat(size).containsExactly(1.75);
    }

    @Test
    void getAllInstancesDicomJson_handlesFloatArrayVrInMetadata() throws IOException {
        // ImageOrientationPatient uses DS VR with a JSON array value
        String metadataJson = """
                {
                  "Study": {
                    "Series": {
                      "1.2.3.4.5.1": {
                        "DICOM": {},
                        "Instances": {
                          "1.2.3.4.5.1.1": {
                            "DICOM": {
                              "ImageOrientationPatient": ["-0.5", "0.866", "0.0", "0.0", "0.0", "1.0"]
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;
        stubGetImageSetMetadata(metadataJson);

        Map<String, List<Map<String, Object>>> result =
                healthImagingService.getAllInstancesDicomJson("image-set-abc");

        assertThat(result).containsKey("1.2.3.4.5.1.1");
        // ImageOrientationPatient → tag 00200037, DS VR, should be list of doubles
        @SuppressWarnings("unchecked")
        List<Double> orient = (List<Double>) ((Map<String, Object>) result.get("1.2.3.4.5.1.1").get(0).get("00200037")).get("Value");
        assertThat(orient).hasSize(6).contains(-0.5, 0.866);
    }

    // resolveFrameId

    @Test
    void resolveFrameId_returnsCorrectFrameFromList() {
        DicomImage image = new DicomImage();
        image.setFrameIds("[\"frame-001\",\"frame-002\",\"frame-003\"]");

        assertThat(healthImagingService.resolveFrameId(image, 1)).isEqualTo("frame-001");
        assertThat(healthImagingService.resolveFrameId(image, 2)).isEqualTo("frame-002");
        assertThat(healthImagingService.resolveFrameId(image, 3)).isEqualTo("frame-003");
    }

    @Test
    void resolveFrameId_returnsNullWhenFrameNumberOutOfRange() {
        DicomImage image = new DicomImage();
        image.setFrameIds("[\"frame-001\"]");

        assertThat(healthImagingService.resolveFrameId(image, 0)).isNull();
        assertThat(healthImagingService.resolveFrameId(image, 2)).isNull();
    }

    @Test
    void resolveFrameId_fallsBackToImageFrameId_ForFrame1_WhenFrameIdsNull() {
        DicomImage image = new DicomImage();
        image.setFrameIds(null);
        image.setImageFrameId("legacy-frame-001");

        assertThat(healthImagingService.resolveFrameId(image, 1)).isEqualTo("legacy-frame-001");
    }

    @Test
    void resolveFrameId_returnsNull_ForFrame2_WhenFrameIdsNullAndLegacyRecord() {
        DicomImage image = new DicomImage();
        image.setFrameIds(null);
        image.setImageFrameId("legacy-frame-001");

        assertThat(healthImagingService.resolveFrameId(image, 2)).isNull();
    }

    // resolveFrameIdForSop

    @Test
    void resolveFrameIdForSop_returnsMappedFrameId_WhenSopInMap() {
        DicomImage image = new DicomImage();
        image.setSopFrameMap("{\"1.2.3.4.5.1.1\":\"frame-001\",\"1.2.3.4.5.1.2\":\"frame-002\"}");

        assertThat(healthImagingService.resolveFrameIdForSop(image, "1.2.3.4.5.1.1", 1)).isEqualTo("frame-001");
        assertThat(healthImagingService.resolveFrameIdForSop(image, "1.2.3.4.5.1.2", 1)).isEqualTo("frame-002");
    }

    @Test
    void resolveFrameIdForSop_fallsBackToResolveFrameId_WhenSopNotInMap() {
        DicomImage image = new DicomImage();
        image.setSopFrameMap("{\"other-sop\":\"frame-x\"}");
        image.setFrameIds("[\"frame-001\"]");

        // SOP not in map → falls back to resolveFrameId(image, frameNumber)
        assertThat(healthImagingService.resolveFrameIdForSop(image, "1.2.3.4.5.1.1", 1)).isEqualTo("frame-001");
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

    private void stubGetImageSetMetadata(String json) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(json.getBytes());
            }
            ResponseInputStream<GetImageSetMetadataResponse> mockResponse =
                    new ResponseInputStream<>(mock(GetImageSetMetadataResponse.class),
                            new ByteArrayInputStream(baos.toByteArray()));
            when(medicalImagingClient.getImageSetMetadata(any(GetImageSetMetadataRequest.class)))
                    .thenReturn(mockResponse);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

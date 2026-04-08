package com.green.imagecore.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "dicom_images")
public class DicomImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * S3 prefix (folder) containing all DICOM files for this series upload,
     * e.g. {@code dicom/{userId}/{batchId}/}. Used for bulk delete via ListObjectsV2.
     */
    @Column(name = "s3_key", nullable = false, length = 512)
    private String s3Key;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /** Number of DICOM instances in this series upload. */
    @Column(name = "file_count", nullable = false)
    private Integer fileCount = 1;

    // ID of the HealthImaging import job, populated immediately after StartDICOMImportJob
    @Column(name = "health_imaging_job_id", columnDefinition = "text")
    private String healthImagingJobId;

    // ID of the image set created in the HealthImaging datastore, populated once import COMPLETED
    @Column(name = "image_set_id", columnDefinition = "text")
    private String imageSetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "import_status", nullable = false, length = 32)
    private ImportStatus importStatus;

    @Column(name = "uploaded_at", nullable = false, insertable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "study_instance_uid", columnDefinition = "text")
    private String studyInstanceUid;

    @Column(name = "series_instance_uid", columnDefinition = "text")
    private String seriesInstanceUid;

    @Column(name = "sop_instance_uid", columnDefinition = "text")
    private String sopInstanceUid;

    @Column(name = "study_description", columnDefinition = "text")
    private String studyDescription;

    @Column(name = "series_description", columnDefinition = "text")
    private String seriesDescription;

    @Column(name = "body_part", length = 64)
    private String bodyPart;

    @Column(name = "modality", length = 16)
    private String modality;

    @Column(name = "patient_id", length = 64)
    private String patientId;

    @Column(name = "study_date")
    private LocalDate studyDate;

    @Column(name = "physician", length = 255)
    private String physician;

    @Column(name = "frame_count", nullable = false)
    @Builder.Default
    private int frameCount = 1;

    @Column(name = "image_frame_id", columnDefinition = "text")
    private String imageFrameId;

    // DICOM tag 00200013 — used to sort slices within a series across multiple DB records
    @Column(name = "instance_number")
    private Integer instanceNumber;

    // Ordered JSON array of HealthImaging frame IDs for all frames in this image set.
    // For single-frame uploads this is a one-element array; for true multi-frame DICOMs
    // it contains all frame IDs in acquisition order.
    @Column(name = "frame_ids", columnDefinition = "text")
    private String frameIds;

    // Ordered JSON array of all SOP instance UIDs in this image set, sorted by InstanceNumber.
    // For batch uploads (N slices in one DB row) this holds all N SOP UIDs.
    // Example: ["1.2.3.4.5", "1.2.3.4.6", ...]
    @Column(name = "sop_instance_uids", columnDefinition = "text")
    private String sopInstanceUids;

    // JSON object mapping each SOP UID to its HealthImaging frame ID.
    // Used by the WADO-RS controller to resolve the correct frame for each slice in a batch.
    // Example: {"1.2.3.4.5": "frameId1", "1.2.3.4.6": "frameId2"}
    @Column(name = "sop_frame_map", columnDefinition = "text")
    private String sopFrameMap;
}

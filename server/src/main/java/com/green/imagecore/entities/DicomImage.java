package com.green.imagecore.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

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
}

package com.green.imagecore.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * One row in the DICOM catalog — represents a single HealthImaging imageSet
 * (i.e. one DICOM series), regardless of how many DB records it spans.
 *
 * imageSetId is used as the stable grouping key: AWS HealthImaging merges all
 * uploads sharing the same Study/Series Instance UIDs into one imageSet, so this
 * collapses individually-uploaded slices into a single catalog row.
 */
@Getter
@Builder
public class DicomSeriesGroupDto {

    /** Stable row key for the catalog table (imageSetId, or "__pending__{id}" for not-yet-imported rows). */
    private final String key;

    /** HealthImaging imageSetId. Null for images still in PENDING/SUBMITTED state. */
    private final String imageSetId;

    /** Human-readable series label: seriesDescription → studyDescription → imageSetId → filename. */
    private final String displayName;

    private final String modality;
    private final String bodyPart;

    /** ISO date string (yyyy-MM-dd), or null if not yet populated from metadata. */
    private final String studyDate;

    /**
     * Number of DICOM instances (slices) in this series.
     * Computed from frame_count, which populateMetadata() sets to the actual SOP instance count
     * after the HealthImaging import job completes.
     */
    private final int instanceCount;

    /** Most advanced importStatus across all DB records in this group. */
    private final String status;

    private final String seriesInstanceUid;
    private final String studyInstanceUid;

    /** IDs of all DicomImage DB records in this group — used by the delete endpoint. */
    private final List<Long> imageIds;
}

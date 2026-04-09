package com.green.imagecore.dto;

import com.green.imagecore.entities.ImportStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Data transfer object for the DICOM Image
 */
@Getter
@Setter
public class DicomImageDto {
    private Long id;
    private String filename;
    private Long fileSize;
    private Integer fileCount;
    private ImportStatus importStatus;
    private String imageSetId;
    private String uploadedAt;
    private String studyInstanceUid;
    private String seriesInstanceUid;
    private String sopInstanceUid;
    private String studyDescription;
    private String seriesDescription;
    private String bodyPart;
    private String modality;
    private String patientId;
    private String studyDate;
    private String physician;
    private int frameCount;
    private Integer instanceNumber;
    /** All SOP instance UIDs in this image set, sorted by InstanceNumber. Populated for batch uploads. */
    private List<String> sopInstanceUids;
}

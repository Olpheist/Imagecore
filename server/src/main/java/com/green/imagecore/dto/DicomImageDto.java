package com.green.imagecore.dto;

import com.green.imagecore.entities.ImportStatus;
import lombok.Getter;
import lombok.Setter;


/**
 * Data transfer object for the DICOM Image
 */
@Getter
@Setter
public class DicomImageDto {
    private Long id;
    private String filename;
    private Long fileSize;
    private ImportStatus importStatus;
    private String imageSetId;
    private String uploadedAt;
}

package com.green.imagecore.dto;

import com.green.imagecore.entities.DicomImage;
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

    public static DicomImageDto from(DicomImage image) {
        DicomImageDto dto = new DicomImageDto();
        dto.setId(image.getId());
        dto.setFilename(image.getFilename());
        dto.setFileSize(image.getFileSize());
        dto.setImportStatus(image.getImportStatus());
        dto.setImageSetId(image.getImageSetId());
        dto.setUploadedAt(image.getUploadedAt() != null ? image.getUploadedAt().toString() : null);
        return dto;
    }
}

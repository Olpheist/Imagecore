package com.green.imagecore.mapper;

import com.green.imagecore.dto.DicomImageDto;
import com.green.imagecore.entities.DicomImage;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DicomImageMapper {

    public static DicomImageDto toDto(DicomImage image) {
        if (image == null) return null;
        DicomImageDto dto = new DicomImageDto();
        dto.setId(image.getId());
        dto.setFilename(image.getFilename());
        dto.setFileSize(image.getFileSize());
        dto.setFileCount(image.getFileCount());
        dto.setImportStatus(image.getImportStatus());
        dto.setImageSetId(image.getImageSetId());
        dto.setUploadedAt(image.getUploadedAt() != null ? image.getUploadedAt().toString() : null);
        return dto;
    }

    public static List<DicomImageDto> toDtos(List<DicomImage> images) {
        if (images == null) return Collections.emptyList();
        return images.stream().map(DicomImageMapper::toDto).collect(Collectors.toList());
    }
}

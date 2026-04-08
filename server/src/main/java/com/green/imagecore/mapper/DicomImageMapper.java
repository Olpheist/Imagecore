package com.green.imagecore.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.imagecore.dto.DicomImageDto;
import com.green.imagecore.entities.DicomImage;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DicomImageMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        dto.setStudyInstanceUid(image.getStudyInstanceUid());
        dto.setSeriesInstanceUid(image.getSeriesInstanceUid());
        dto.setSopInstanceUid(image.getSopInstanceUid());
        dto.setStudyDescription(image.getStudyDescription());
        dto.setSeriesDescription(image.getSeriesDescription());
        dto.setBodyPart(image.getBodyPart());
        dto.setModality(image.getModality());
        dto.setPatientId(image.getPatientId());
        dto.setStudyDate(image.getStudyDate() != null ? image.getStudyDate().toString() : null);
        dto.setPhysician(image.getPhysician());
        dto.setFrameCount(image.getFrameCount());
        dto.setInstanceNumber(image.getInstanceNumber());
        if (image.getSopInstanceUids() != null) {
            try {
                dto.setSopInstanceUids(MAPPER.readValue(image.getSopInstanceUids(), new TypeReference<>() {}));
            } catch (Exception ignored) {}
        }
        return dto;
    }

    public static List<DicomImageDto> toDtos(List<DicomImage> images) {
        if (images == null) return Collections.emptyList();
        return images.stream().map(DicomImageMapper::toDto).collect(Collectors.toList());
    }
}

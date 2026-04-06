package com.green.imagecore.controller;

import com.green.imagecore.dto.DicomImageDto;
import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.mapper.DicomImageMapper;
import com.green.imagecore.service.DicomCatalogService;
import com.green.imagecore.service.DicomUploadService;
import com.green.imagecore.service.HealthImagingService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/images")
public class ImageController {

    private final DicomUploadService dicomUploadService;
    private final HealthImagingService healthImagingService;
    private final DicomCatalogService dicomCatalogService;

    /**
     * Single-file upload. Validates the file, stores it in S3, and queues a HealthImaging import job.
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('CLINICIAN', 'RESEARCHER')")
    public ResponseEntity<DicomImageDto> upload(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        Long userId = parseUserId(authentication);
        DicomImage image = dicomUploadService.upload(file, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(DicomImageMapper.toDto(image));
    }

    /**
     * Batch upload. All files are treated as a single series — they are uploaded to a shared
     * S3 prefix and processed by one HealthImaging import job. Returns a single DTO representing
     * the series record.
     */
    @PostMapping("/upload-batch")
    @PreAuthorize("hasAnyRole('CLINICIAN', 'RESEARCHER')")
    public ResponseEntity<DicomImageDto> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            Authentication authentication
    ) {
        Long userId = parseUserId(authentication);
        DicomImage image = dicomUploadService.uploadBatch(files, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(DicomImageMapper.toDto(image));
    }

    /**
     * Returns all DICOM images uploaded by the authenticated user, ordered by upload date descending.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLINICIAN', 'RESEARCHER')")
    public ResponseEntity<List<DicomImageDto>> listImages(Authentication authentication) {
        Long userId = parseUserId(authentication);
        List<DicomImage> images = dicomCatalogService.findAllForUser(userId);
        return ResponseEntity.ok(DicomImageMapper.toDtos(images));
    }

    /**
     * Syncs the import status of a specific image from HealthImaging and returns the updated record.
     * Returns 404 if the image does not exist or does not belong to the authenticated user.
     */
    @GetMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('CLINICIAN', 'RESEARCHER')")
    public ResponseEntity<DicomImageDto> getStatus(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long userId = parseUserId(authentication);
        DicomImage image = dicomCatalogService.findByIdAndUser(id, userId);
        DicomImage updated = healthImagingService.syncImportStatus(image);
        return ResponseEntity.ok(DicomImageMapper.toDto(updated));
    }

    /**
     * Deletes a DICOM image by ID. Ownership is enforced.
     * A missing or unowned image returns 404 (not 403) to avoid leaking existence to other users.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLINICIAN', 'RESEARCHER')")
    public ResponseEntity<Void> deleteImage(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long userId = parseUserId(authentication);
        dicomCatalogService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    // TODO: analysis job submission — implement in a future story
    // POST /api/images/{imageId}/jobs
    // Body: { toolId: Long }
    // Will create an AnalysisJob record (PENDING) linking image + tool + user

    private Long parseUserId(Authentication authentication) {
        return Long.parseLong(
                ((JwtAuthenticationToken) authentication).getToken().getClaimAsString("uid")
        );
    }
}

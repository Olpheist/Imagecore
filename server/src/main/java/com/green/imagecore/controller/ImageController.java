package com.green.imagecore.controller;

import com.green.imagecore.dto.DicomImageDto;
import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.mapper.DicomImageMapper;
import com.green.imagecore.service.DicomCatalogService;
import com.green.imagecore.service.DicomUploadService;
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
    private final DicomCatalogService dicomCatalogService;

    /**
     * Accepts a DICOM file upload. Restricted to users with the CLINICIAN role.
     * The file is validated for the DICOM magic bytes and stored in S3 under
     * a key namespaced to the authenticated user: dicom/{userId}/{uuid}.dcm
     * Single-file upload. Restricted to CLINICIAN role.
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
     * Multi-file upload. Restricted to CLINICIAN role.
     */
    @PostMapping("/upload-batch")
    @PreAuthorize("hasAnyRole('CLINICIAN', 'RESEARCHER')")
    public ResponseEntity<List<DicomImageDto>> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            Authentication authentication
    ) {
        Long userId = parseUserId(authentication);
        List<DicomImage> images = dicomUploadService.uploadBatch(files, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(DicomImageMapper.toDtos(images));
    }

    /**
     * List the caller's uploaded DICOM images. Accessible to CLINICIAN and RESEARCHER.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLINICIAN', 'RESEARCHER')")
    public ResponseEntity<List<DicomImageDto>> listImages(Authentication authentication) {
        Long userId = parseUserId(authentication);
        List<DicomImage> images = dicomCatalogService.findAllForUser(userId);
        return ResponseEntity.ok(DicomImageMapper.toDtos(images));
    }

    /**
     * Delete a DICOM image by ID. Ownership is enforced — a missing or unowned image
     * returns 404 (not 403) to avoid leaking existence to other users.
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

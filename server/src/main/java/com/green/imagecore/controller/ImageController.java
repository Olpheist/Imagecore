package com.green.imagecore.controller;

import com.green.imagecore.service.DicomUploadService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@AllArgsConstructor
@RequestMapping("/api/images")
public class ImageController {

    private final DicomUploadService dicomUploadService;

    /**
     * Accepts a DICOM file upload. Restricted to users with the CLINICIAN role.
     * The file is validated for the DICOM magic bytes and stored in S3 under
     * a key namespaced to the authenticated user: dicom/{userId}/{uuid}.dcm
     */
    @PostMapping("/upload")
    @PreAuthorize("hasRole('CLINICIAN')")
    public ResponseEntity<UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        String userId = ((JwtAuthenticationToken) authentication).getToken().getClaimAsString("uid");
        String key = dicomUploadService.upload(file, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(new UploadResponse(key));
    }

    public record UploadResponse(String key) {}
}

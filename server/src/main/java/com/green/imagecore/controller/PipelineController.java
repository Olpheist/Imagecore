package com.green.imagecore.controller;

import com.green.imagecore.dto.PipelineDto;
import com.green.imagecore.service.PipelineService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/images")
public class PipelineController {

    private final PipelineService pipelineService;

    @PostMapping("/{imageId}/pipelines")
    @PreAuthorize("hasAnyRole('CLINICIAN', 'RESEARCHER')")
    public ResponseEntity<PipelineDto> submitPipeline(
            @PathVariable Long imageId,
            @RequestBody SubmitPipelineRequest request,
            Authentication authentication
    ) {
        Long userId = parseUserId(authentication);
        PipelineDto dto = pipelineService.submitPipeline(imageId, request.toolIds(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/{imageId}/pipelines/latest")
    @PreAuthorize("hasAnyRole('CLINICIAN', 'RESEARCHER')")
    public ResponseEntity<PipelineDto> getLatestPipeline(
            @PathVariable Long imageId,
            Authentication authentication
    ) {
        Long userId = parseUserId(authentication);
        return pipelineService.findLatestForImage(imageId, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record SubmitPipelineRequest(List<Long> toolIds) {}

    private Long parseUserId(Authentication authentication) {
        return Long.parseLong(
                ((JwtAuthenticationToken) authentication).getToken().getClaimAsString("uid")
        );
    }
}

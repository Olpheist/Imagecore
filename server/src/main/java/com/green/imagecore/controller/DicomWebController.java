package com.green.imagecore.controller;

import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.ImportStatus;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.DicomImageRepository;
import com.green.imagecore.service.HealthImagingService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DICOMweb-compatible endpoints (QIDO-RS + WADO-RS) that proxy to AWS HealthImaging.
 * All queries are scoped to the authenticated user's images, enforcing ownership.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dicomweb")
public class DicomWebController {

    private static final String DICOM_JSON = "application/dicom+json";

    private final DicomImageRepository dicomImageRepository;
    private final HealthImagingService healthImagingService;

    // QIDO-RS: Query

    /**
     * QIDO-RS: Search for studies. Returns all completed studies owned by the authenticated user.
     */
    @GetMapping(value = "/studies", produces = DICOM_JSON)
    @PreAuthorize("hasAnyRole('CLINICIAN', 'RESEARCHER')")
    public List<Map<String, Object>> searchStudies(Authentication authentication) {
        Long userId = parseUserId(authentication);
        List<DicomImage> images = dicomImageRepository
                .findByUserIdAndImportStatusOrderByUploadedAtDesc(userId, ImportStatus.COMPLETED);

        return images.stream().map(this::buildStudyJson).collect(Collectors.toList());
    }

    /**
     * QIDO-RS: Search for series within a study.
     */
    @GetMapping(value = "/studies/{studyUID}/series", produces = DICOM_JSON)
    @PreAuthorize("hasAnyRole('CLINICIAN', 'RESEARCHER')")
    public List<Map<String, Object>> searchSeries(
            @PathVariable String studyUID,
            Authentication authentication) {
        Long userId = parseUserId(authentication);
        List<DicomImage> images = dicomImageRepository
                .findByUserIdAndStudyInstanceUidAndImportStatus(userId, studyUID, ImportStatus.COMPLETED);

        return images.stream().map(this::buildSeriesJson).collect(Collectors.toList());
    }

    /**
     * QIDO-RS: Search for instances within a series.
     */
    @GetMapping(value = "/studies/{studyUID}/series/{seriesUID}/instances", produces = DICOM_JSON)
    @PreAuthorize("hasAnyRole('CLINICIAN', 'RESEARCHER')")
    public List<Map<String, Object>> searchInstances(
            @PathVariable String studyUID,
            @PathVariable String seriesUID,
            Authentication authentication) {
        Long userId = parseUserId(authentication);
        List<DicomImage> images = dicomImageRepository
                .findByUserIdAndStudyInstanceUidAndImportStatus(userId, studyUID, ImportStatus.COMPLETED);

        return images.stream()
                .filter(img -> seriesUID.equals(img.getSeriesInstanceUid()))
                .map(this::buildInstanceJson)
                .collect(Collectors.toList());
    }

    // WADO-RS: Retrieve metadata

    /**
     * WADO-RS: Retrieve instance metadata.
     * Returns the full DICOM JSON from HealthImaging (including pixel module tags such as
     * Rows, Columns, BitsAllocated, SamplesPerPixel, PhotometricInterpretation) so that
     * Cornerstone can decode the frame data without additional out-of-band metadata.
     */
    @GetMapping(value = "/studies/{studyUID}/series/{seriesUID}/instances/{sopUID}/metadata",
            produces = DICOM_JSON)
    @PreAuthorize("hasAnyRole('CLINICIAN', 'RESEARCHER')")
    public List<Map<String, Object>> getInstanceMetadata(
            @PathVariable String studyUID,
            @PathVariable String seriesUID,
            @PathVariable String sopUID,
            Authentication authentication) throws java.io.IOException {
        Long userId = parseUserId(authentication);
        DicomImage image = findOwnedInstance(sopUID, userId);
        List<Map<String, Object>> fullJson = healthImagingService.getInstanceDicomJson(image, sopUID);
        // Fall back to the hand-built partial JSON if HealthImaging metadata is unavailable
        return fullJson.isEmpty() ? List.of(buildInstanceJson(image)) : fullJson;
    }

    /**
     * Bulk metadata endpoint: returns DICOM JSON for ALL instances in an image set in one call.
     * The frontend uses this instead of fetching /instances/{sop}/metadata N times, reducing
     * metadata HTTP calls from N → 1 and AWS GetImageSetMetadata calls from N → 1.
     *
     * Response shape: { "sopUid1": [{dicomJson}], "sopUid2": [{dicomJson}], ... }
     */
    @GetMapping(value = "/imagesets/{imageSetId}/instances/metadata", produces = DICOM_JSON)
    @PreAuthorize("hasAnyRole('CLINICIAN', 'RESEARCHER')")
    public Map<String, List<Map<String, Object>>> getImageSetAllMetadata(
            @PathVariable String imageSetId,
            Authentication authentication) throws java.io.IOException {
        Long userId = parseUserId(authentication);
        // Verify ownership before serving any metadata
        dicomImageRepository.findByImageSetIdAndUserId(imageSetId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("ImageSet not found: " + imageSetId));
        return healthImagingService.getAllInstancesDicomJson(imageSetId);
    }

    // WADO-RS: Retrieve frames

    /**
     * WADO-RS: Retrieve a specific frame from an instance.
     * Streams the frame data from AWS HealthImaging directly to the response.
     */
    @GetMapping("/studies/{studyUID}/series/{seriesUID}/instances/{sopUID}/frames/{frameNumber}")
    @PreAuthorize("hasAnyRole('CLINICIAN', 'RESEARCHER')")
    public void getFrame(
            @PathVariable String studyUID,
            @PathVariable String seriesUID,
            @PathVariable String sopUID,
            @PathVariable int frameNumber,
            Authentication authentication,
            HttpServletResponse response) throws IOException {
        Long userId = parseUserId(authentication);
        DicomImage image = findOwnedInstance(sopUID, userId);

        String frameId = healthImagingService.resolveFrameIdForSop(image, sopUID, frameNumber);
        if (frameId == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    frameNumber < 1 ? "Frame number must be >= 1" : "Frame number out of range or no frame data");
            return;
        }

        // image/jphc = HTJ2K — tells Cornerstone which codec to use for pixel decoding
        response.setContentType("image/jphc");
        try {
            healthImagingService.streamImageFrame(image.getImageSetId(), frameId, response.getOutputStream());
        } catch (Exception e) {
            log.error("Failed to stream frame {} (imageSet={}, frameId={}): {}",
                    sopUID, image.getImageSetId(), frameId, e.getMessage());
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_BAD_GATEWAY,
                        "HealthImaging frame retrieval failed");
            }
            return;
        }
        response.flushBuffer();
    }

    // Helpers

    private DicomImage findOwnedInstance(String sopInstanceUid, Long userId) {
        // Use the broader query that also checks sop_instance_uids JSON array,
        // enabling batch-uploaded series (multiple SOPs in one DB row) to be served.
        return dicomImageRepository.findBySopUidForUser(sopInstanceUid, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Instance not found: " + sopInstanceUid));
    }

    private Long parseUserId(Authentication authentication) {
        return Long.parseLong(
                ((JwtAuthenticationToken) authentication).getToken().getClaimAsString("uid"));
    }

    /**
     * Builds a DICOM JSON object with study-level tags.
     * Tag IDs follow the DICOM standard (e.g. 0020000D = StudyInstanceUID).
     * Each tag is: { "vr": "XX", "Value": ["..."] }
     */
    private Map<String, Object> buildStudyJson(DicomImage img) {
        Map<String, Object> obj = new LinkedHashMap<>();
        putDicomTag(obj, "0020000D", "UI", img.getStudyInstanceUid());   // StudyInstanceUID
        putDicomTag(obj, "00100020", "LO", img.getPatientId());          // PatientID
        putDicomTag(obj, "00081030", "LO", img.getStudyDescription());   // StudyDescription
        putDicomTag(obj, "00080020", "DA", img.getStudyDate() != null
                ? img.getStudyDate().toString() : null);                  // StudyDate
        putDicomTag(obj, "00080090", "PN", img.getPhysician());          // ReferringPhysicianName
        putDicomTag(obj, "00080060", "CS", img.getModality());           // ModalitiesInStudy
        return obj;
    }

    private Map<String, Object> buildSeriesJson(DicomImage img) {
        Map<String, Object> obj = buildStudyJson(img);
        putDicomTag(obj, "0020000E", "UI", img.getSeriesInstanceUid()); // SeriesInstanceUID
        putDicomTag(obj, "0008103E", "LO", img.getSeriesDescription()); // SeriesDescription
        putDicomTag(obj, "00080060", "CS", img.getModality());          // Modality
        putDicomTag(obj, "00180015", "CS", img.getBodyPart());          // BodyPartExamined
        return obj;
    }

    private Map<String, Object> buildInstanceJson(DicomImage img) {
        Map<String, Object> obj = buildSeriesJson(img);
        putDicomTag(obj, "00080018", "UI", img.getSopInstanceUid());    // SOPInstanceUID
        putDicomTag(obj, "00280008", "IS", String.valueOf(img.getFrameCount())); // NumberOfFrames
        return obj;
    }

    /**
     * Writes a single DICOM tag in standard DICOM JSON format:
     * { "GGGGEEEE": { "vr": "XX", "Value": ["..."] } }
     */
    private void putDicomTag(Map<String, Object> parent, String tag, String vr, String value) {
        Map<String, Object> tagNode = new LinkedHashMap<>();
        tagNode.put("vr", vr);
        if (value != null) {
            tagNode.put("Value", List.of(value));
        }
        parent.put(tag, tagNode);
    }
}

package com.green.imagecore.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.ImportStatus;
import com.green.imagecore.repositories.DicomImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.medicalimaging.MedicalImagingClient;
import software.amazon.awssdk.services.medicalimaging.model.DICOMImportJobProperties;
import software.amazon.awssdk.services.medicalimaging.model.GetDicomImportJobRequest;
import software.amazon.awssdk.services.medicalimaging.model.GetDicomImportJobResponse;
import software.amazon.awssdk.services.medicalimaging.model.JobStatus;
import software.amazon.awssdk.services.medicalimaging.model.GetImageSetMetadataRequest;
import software.amazon.awssdk.services.medicalimaging.model.GetImageSetMetadataResponse;
import software.amazon.awssdk.services.medicalimaging.model.GetImageFrameRequest;
import software.amazon.awssdk.services.medicalimaging.model.GetImageFrameResponse;
import software.amazon.awssdk.services.medicalimaging.model.ImageFrameInformation;
import software.amazon.awssdk.services.medicalimaging.model.StartDicomImportJobRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.*;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import com.fasterxml.jackson.core.type.TypeReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthImagingService {

    private final MedicalImagingClient medicalImagingClient;
    private final S3Client s3Client;
    private final DicomImageRepository dicomImageRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.aws.health-imaging.datastore-id}")
    private String datastoreId;

    @Value("${app.aws.health-imaging.import-role-arn}")
    private String importRoleArn;

    /**
     * Submits a DICOM import job to AWS HealthImaging for the given S3 input prefix.
     *
     * @param inputS3Uri  S3 URI of the folder containing the uploaded DICOM file, e.g. s3://bucket/dicom/1/uuid/
     * @param outputS3Uri S3 URI prefix where HealthImaging will write the import job manifest
     * @return the HealthImaging job ID
     */
    public String startImportJob(String inputS3Uri, String outputS3Uri) {
        StartDicomImportJobRequest request = StartDicomImportJobRequest.builder()
                .datastoreId(datastoreId)
                .dataAccessRoleArn(importRoleArn)
                .inputS3Uri(inputS3Uri)
                .outputS3Uri(outputS3Uri)
                .build();

        String jobId = medicalImagingClient.startDICOMImportJob(request).jobId();
        log.debug("Started HealthImaging import job {} for input {}", jobId, inputS3Uri);
        return jobId;
    }

    /**
     * Checks the current status of a HealthImaging import job and saves the result to the database.
     * If the job has finished successfully, the imageSetId is pulled from the output manifest and saved.
     * If the status is already COMPLETED or FAILED, the method skips the AWS call since there's nothing left to update.
     *
     * @param image the DicomImage whose import status should be refreshed
     * @return the updated DicomImage
     */
    public DicomImage syncImportStatus(DicomImage image) {
        // Skip only when fully resolved AND metadata is complete.
        // Re-run populateMetadata if critical DICOM UIDs are missing even on a COMPLETED record
        // (e.g. a prior metadata parse failed to extract series/study UIDs).
        boolean metadataComplete = image.getSeriesInstanceUid() != null
                && image.getSopInstanceUid() != null
                && image.getSopInstanceUids() != null
                && image.getSopFrameMap() != null;
        boolean fullyResolved = image.getImportStatus() == ImportStatus.FAILED
                || (image.getImportStatus() == ImportStatus.COMPLETED
                    && image.getImageSetId() != null
                    && metadataComplete);
        if (fullyResolved) {
            return image;
        }

        // If already COMPLETED with imageSetId but metadata is incomplete, skip the job status
        // call and just re-populate metadata directly.
        if (image.getImportStatus() == ImportStatus.COMPLETED
                && image.getImageSetId() != null
                && !metadataComplete) {
            log.info("Re-populating missing metadata for imageSet {}", image.getImageSetId());
            populateMetadata(image);
            return dicomImageRepository.save(image);
        }

        GetDicomImportJobResponse response = medicalImagingClient.getDICOMImportJob(
                GetDicomImportJobRequest.builder()
                        .datastoreId(datastoreId)
                        .jobId(image.getHealthImagingJobId())
                        .build()
        );

        DICOMImportJobProperties props = response.jobProperties();
        JobStatus jobStatus = props.jobStatus();
        log.debug("HealthImaging job {} status: {}", image.getHealthImagingJobId(), jobStatus);

        switch (jobStatus) {
            case COMPLETED -> {
                String imageSetId = extractImageSetId(props.outputS3Uri());
                if (imageSetId == null) {
                    // Job-level status is COMPLETED but no imageSet was produced.
                    // This typically means all files failed with customer errors
                    // (e.g. CreateImageSetVersionsException from a DELETED tombstone imageSet
                    // when the same DICOM UIDs were previously imported and then deleted).
                    log.warn("HealthImaging job {} completed but produced no imageSet — marking FAILED. " +
                             "Check the FAILURE/ folder at {} for per-file error details.",
                            image.getHealthImagingJobId(), props.outputS3Uri());
                    image.setImportStatus(ImportStatus.FAILED);
                } else {
                    image.setImageSetId(imageSetId);
                    image.setImportStatus(ImportStatus.COMPLETED);
                    populateMetadata(image);
                }
            }
            case FAILED -> {
                log.warn("HealthImaging import job {} failed: {}",
                        image.getHealthImagingJobId(), props.message());
                image.setImportStatus(ImportStatus.FAILED);
            }
            case IN_PROGRESS -> image.setImportStatus(ImportStatus.IN_PROGRESS);
            case SUBMITTED   -> image.setImportStatus(ImportStatus.SUBMITTED);
            default          -> log.warn("Unrecognised HealthImaging job status: {}", jobStatus);
        }

        return dicomImageRepository.save(image);
    }

    /**
     * Fetches the raw (decompressed) HealthImaging metadata JSON for an image set.
     * Shared by {@link #populateMetadata} and {@link #getInstanceDicomJson}.
     */
    private JsonNode fetchRawMetadata(String imageSetId) throws IOException {
        GetImageSetMetadataRequest request = GetImageSetMetadataRequest.builder()
                .datastoreId(datastoreId)
                .imageSetId(imageSetId)
                .build();
        ResponseInputStream<GetImageSetMetadataResponse> response =
                medicalImagingClient.getImageSetMetadata(request);
        byte[] decompressed = decompressGzip(response);
        return objectMapper.readTree(decompressed);
    }

    /**
     * Returns standard DICOM JSON for a SOP instance, converted from HealthImaging's
     * named-keyword format. The result uses hex tag codes with { "vr": "XX", "Value": [...] }
     * wrappers so that Cornerstone's image loader can resolve the imagePixelModule
     * (SamplesPerPixel, PhotometricInterpretation, Rows, Columns, BitsAllocated, etc.).
     *
     * @param image      the DicomImage record whose HealthImaging imageSet should be queried
     * @param requestedSopUid the specific SOP instance UID requested (may differ from image.getSopInstanceUid()
     *                        for batch uploads where multiple SOPs share one DB row)
     * @return standard DICOM JSON array (one element), or empty list if instance not found
     */
    public List<Map<String, Object>> getInstanceDicomJson(DicomImage image, String requestedSopUid) throws IOException {
        JsonNode root = fetchRawMetadata(image.getImageSetId());
        JsonNode seriesMap = root.path("Study").path("Series");
        for (Iterator<Map.Entry<String, JsonNode>> seriesIt = seriesMap.fields(); seriesIt.hasNext(); ) {
            Map.Entry<String, JsonNode> seriesEntry = seriesIt.next();
            JsonNode instanceNode = seriesEntry.getValue().path("Instances").path(requestedSopUid);
            if (!instanceNode.isMissingNode()) {
                JsonNode instanceDicom = instanceNode.path("DICOM");
                Map<String, Object> dicomJson = convertToDicomJson(instanceDicom);
                // Inject series-level tags that Cornerstone also needs
                JsonNode seriesDicom = seriesEntry.getValue().path("DICOM");
                dicomJson.putAll(convertToDicomJson(seriesDicom));
                // Ensure TransferSyntaxUID is present — Cornerstone uses this to select the
                // HTJ2K codec. HealthImaging always stores frames as HTJ2K lossless (202).
                dicomJson.putIfAbsent("00020010", Map.of("vr", "UI", "Value", List.of("1.2.840.10008.1.2.4.202")));
                log.debug("Returning DICOM JSON for instance {} (imageSet {}), {} tags",
                        requestedSopUid, image.getImageSetId(), dicomJson.size());
                return List.of(dicomJson);
            }
        }
        log.warn("Instance {} not found in HealthImaging metadata for imageSet {}",
                requestedSopUid, image.getImageSetId());
        return List.of();
    }

    /**
     * Returns DICOM JSON for ALL instances in an image set in a single call.
     * Calls {@link #fetchRawMetadata} exactly once regardless of how many instances the set has,
     * so the caller can register all metadata in one round-trip instead of one per SOP.
     *
     * @param imageSetId the HealthImaging image set ID
     * @return map of sopInstanceUid → standard DICOM JSON (one-element list per the WADO-RS spec)
     */
    public Map<String, List<Map<String, Object>>> getAllInstancesDicomJson(String imageSetId) throws IOException {
        JsonNode root = fetchRawMetadata(imageSetId);
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();

        JsonNode seriesMap = root.path("Study").path("Series");
        for (Iterator<Map.Entry<String, JsonNode>> seriesIt = seriesMap.fields(); seriesIt.hasNext(); ) {
            Map.Entry<String, JsonNode> seriesEntry = seriesIt.next();
            Map<String, Object> seriesJson = convertToDicomJson(seriesEntry.getValue().path("DICOM"));

            JsonNode instancesMap = seriesEntry.getValue().path("Instances");
            for (Iterator<Map.Entry<String, JsonNode>> instIt = instancesMap.fields(); instIt.hasNext(); ) {
                Map.Entry<String, JsonNode> instEntry = instIt.next();
                String sopUid = instEntry.getKey();
                Map<String, Object> dicomJson = new LinkedHashMap<>(convertToDicomJson(instEntry.getValue().path("DICOM")));
                dicomJson.putAll(seriesJson); // merge series-level tags
                // Ensure TransferSyntaxUID is present — Cornerstone uses 00020010 to select
                // the HTJ2K codec. HealthImaging always stores frames as HTJ2K lossless (202).
                dicomJson.putIfAbsent("00020010", Map.of("vr", "UI", "Value", List.of("1.2.840.10008.1.2.4.202")));
                result.put(sopUid, List.of(dicomJson));
            }
        }
        log.debug("getAllInstancesDicomJson: imageSet {} → {} instances", imageSetId, result.size());
        return result;
    }

    private Map<String, Object> flattenDicom(JsonNode dicomNode) {
        Map<String, Object> map = new HashMap<>();

        Iterator<Map.Entry<String, JsonNode>> fields = dicomNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();

            String key = entry.getKey();
            JsonNode value = entry.getValue();

            if (value.isArray()) {
                List<Object> list = new ArrayList<>();
                for (JsonNode v : value) {
                    list.add(v.isNumber() ? v.numberValue() : v.asText().trim());
                }
                map.put(key, list);
            } else if (value.isNumber()) {
                map.put(key, value.numberValue());
            } else if (value.isNull()) {
                // skip nulls (important)
            } else {
                map.put(key, value.asText().trim());
            }
        }

        return map;
    }

    /**
     * Converts HealthImaging's named-keyword DICOM format to standard DICOM JSON.
     *
     * HealthImaging stores attributes as: { "StudyInstanceUID": "1.2.3...", "Rows": 512 }
     * Standard DICOM JSON expects: { "0020000D": { "vr": "UI", "Value": ["1.2.3..."] } }
     *
     * Only tags present in KEYWORD_TO_TAG_VR are converted; unknown keywords are skipped.
     * Null values are emitted as { "vr": "XX" } (no Value key), per DICOM JSON spec.
     */
    private Map<String, Object> convertToDicomJson(JsonNode namedKeyDicom) {
        Map<String, Object> result = new LinkedHashMap<>();
        namedKeyDicom.fields().forEachRemaining(entry -> {
            String keyword = entry.getKey();
            String[] tagVr = KEYWORD_TO_TAG_VR.get(keyword);
            if (tagVr == null) return; // unknown keyword — skip

            String tag = tagVr[0];
            String vr  = tagVr[1];
            JsonNode valueNode = entry.getValue();

            Map<String, Object> tagObj = new LinkedHashMap<>();
            tagObj.put("vr", vr);

            if (!valueNode.isNull()) {
                String rawValue = valueNode.asText();
                // Numeric VRs: US, SS, UL, SL, FL, FD, IS, DS — emit as number in Value array
                if (vr.equals("US") || vr.equals("SS") || vr.equals("UL") || vr.equals("SL")) {
                    try { tagObj.put("Value", List.of(Integer.parseInt(rawValue))); }
                    catch (NumberFormatException e) { tagObj.put("Value", List.of(rawValue)); }
                } else if (vr.equals("FL") || vr.equals("FD") || vr.equals("DS")) {
                    try {
                        List<Double> values = new ArrayList<>();

                        if (valueNode.isArray()) {
                            // Handle cases like ["-28.6299", "-133.47"]
                            for (JsonNode node : valueNode) {
                                String val = node.asText().trim();
                                if (!val.isEmpty()) {
                                    try { values.add(Double.parseDouble(val)); }
                                    catch (NumberFormatException e) { log.warn("Invalid double in array: {}", val); }
                                }
                            }
                        } else {
                            // Handle cases like "1.29999" (Single String)
                            String rawVal = valueNode.asText().trim();
                            // Remove potential leftover brackets/quotes if the string is literal "[1.2, 3.4]"
                            String cleaned = rawVal.replace("[", "").replace("]", "").replace("\"", "");

                            for (String part : cleaned.split(",\\s*")) {
                                if (!part.isEmpty()) {
                                    try { values.add(Double.parseDouble(part)); }
                                    catch (NumberFormatException e) { log.warn("Invalid double in string: {}", part); }
                                }
                            }
                        }

                        tagObj.put("Value", values);
                    } catch (NumberFormatException e) { tagObj.put("Value", List.of(rawValue)); }
                } else {
                    tagObj.put("Value", List.of(rawValue));
                }
            }
            result.put(tag, tagObj);
        });
        return result;
    }

    /**
     * DICOM keyword → [hexTag, VR] mapping for HealthImaging named-key metadata.
     * Covers study, series, and instance-level attributes needed by Cornerstone's
     * imagePixelModule, imagePlaneModule, and general metadata providers.
     */
    private static final Map<String, String[]> KEYWORD_TO_TAG_VR = Map.ofEntries(
        // File Meta / Transfer Syntax — HealthImaging always stores frames as HTJ2K;
        // Cornerstone reads 00020010 from the /metadata response to pick the right codec.
        Map.entry("TransferSyntaxUID",          new String[]{"00020010", "UI"}),
        // Patient
        Map.entry("PatientID",                  new String[]{"00100020", "LO"}),
        Map.entry("PatientName",                new String[]{"00100010", "PN"}),
        Map.entry("PatientBirthDate",           new String[]{"00100030", "DA"}),
        Map.entry("PatientSex",                 new String[]{"00100040", "CS"}),
        Map.entry("PatientAge",                 new String[]{"00101010", "AS"}),
        Map.entry("PatientSize",                new String[]{"00101020", "DS"}),
        Map.entry("PatientWeight",              new String[]{"00101030", "DS"}),
        // Study
        Map.entry("StudyInstanceUID",           new String[]{"0020000D", "UI"}),
        Map.entry("StudyDescription",           new String[]{"00081030", "LO"}),
        Map.entry("StudyDate",                  new String[]{"00080020", "DA"}),
        Map.entry("StudyTime",                  new String[]{"00080030", "TM"}),
        Map.entry("StudyID",                    new String[]{"00200010", "SH"}),
        Map.entry("AccessionNumber",            new String[]{"00080050", "SH"}),
        Map.entry("ReferringPhysicianName",     new String[]{"00080090", "PN"}),
        // Series
        Map.entry("SeriesInstanceUID",          new String[]{"0020000E", "UI"}),
        Map.entry("SeriesDescription",          new String[]{"0008103E", "LO"}),
        Map.entry("Modality",                   new String[]{"00080060", "CS"}),
        Map.entry("BodyPartExamined",           new String[]{"00180015", "CS"}),
        Map.entry("SeriesDate",                 new String[]{"00080021", "DA"}),
        Map.entry("SeriesTime",                 new String[]{"00080031", "TM"}),
        Map.entry("SeriesNumber",               new String[]{"00200011", "IS"}),
        // Instance / SOP
        Map.entry("SOPInstanceUID",             new String[]{"00080018", "UI"}),
        Map.entry("SOPClassUID",                new String[]{"00080016", "UI"}),
        Map.entry("InstanceNumber",             new String[]{"00200013", "IS"}),
        // Image Pixel Module — critical for Cornerstone decoder
        Map.entry("SamplesPerPixel",            new String[]{"00280002", "US"}),
        Map.entry("PhotometricInterpretation",  new String[]{"00280004", "CS"}),
        Map.entry("Rows",                       new String[]{"00280010", "US"}),
        Map.entry("Columns",                    new String[]{"00280011", "US"}),
        Map.entry("BitsAllocated",              new String[]{"00280100", "US"}),
        Map.entry("BitsStored",                 new String[]{"00280101", "US"}),
        Map.entry("HighBit",                    new String[]{"00280102", "US"}),
        Map.entry("PixelRepresentation",        new String[]{"00280103", "US"}),
        Map.entry("NumberOfFrames",             new String[]{"00280008", "IS"}),
        Map.entry("SmallestImagePixelValue",    new String[]{"00280106", "US"}),
        Map.entry("LargestImagePixelValue",     new String[]{"00280107", "US"}),
        // VOI LUT — window/level defaults
        Map.entry("WindowCenter",               new String[]{"00281050", "DS"}),
        Map.entry("WindowWidth",                new String[]{"00281051", "DS"}),
        Map.entry("RescaleIntercept",           new String[]{"00281052", "DS"}),
        Map.entry("RescaleSlope",               new String[]{"00281053", "DS"}),
        Map.entry("RescaleType",                new String[]{"00281054", "LO"}),
        // Image Plane Module — position / orientation / spacing
        Map.entry("PixelSpacing",               new String[]{"00280030", "DS"}),
        Map.entry("ImageOrientationPatient",    new String[]{"00200037", "DS"}),
        Map.entry("ImagePositionPatient",       new String[]{"00200032", "DS"}),
        Map.entry("SliceThickness",             new String[]{"00180050", "DS"}),
        Map.entry("SliceLocation",              new String[]{"00201041", "DS"})
    );

    /**
     * Fetches image set metadata from HealthImaging and populates DICOM tags on the entity.
     *
     * HealthImaging metadata uses human-readable keyword names as JSON keys
     * (e.g. "StudyInstanceUID", "SeriesDescription") with plain string/number values —
     * NOT the standard DICOM JSON hex-tag format. All field access uses the keyword name.
     *
     * Structure: Patient.DICOM, Study.DICOM, Study.Series.{seriesUID}.DICOM,
     *            Study.Series.{seriesUID}.Instances.{sopUID}.DICOM / ImageFrames
     */
    public void populateMetadata(DicomImage image) {
        try {
            JsonNode root = fetchRawMetadata(image.getImageSetId());

            // Patient level — keyword "PatientID"
            JsonNode patientDicom = root.path("Patient").path("DICOM");
            image.setPatientId(namedTag(patientDicom, "PatientID"));

            // Study level — all keyword names, not hex codes
            JsonNode studyDicom = root.path("Study").path("DICOM");
            image.setStudyInstanceUid(namedTag(studyDicom, "StudyInstanceUID"));
            image.setStudyDescription(namedTag(studyDicom, "StudyDescription"));
            image.setPhysician(namedTag(studyDicom, "ReferringPhysicianName"));

            String studyDateStr = namedTag(studyDicom, "StudyDate");
            if (studyDateStr != null) {
                image.setStudyDate(parseDicomDate(studyDateStr));
            }

            // Series level — the map key IS the SeriesInstanceUID
            JsonNode seriesMap = root.path("Study").path("Series");
            Iterator<Map.Entry<String, JsonNode>> seriesIter = seriesMap.fields();
            if (seriesIter.hasNext()) {
                Map.Entry<String, JsonNode> seriesEntry = seriesIter.next();
                image.setSeriesInstanceUid(seriesEntry.getKey());

                JsonNode seriesDicom = seriesEntry.getValue().path("DICOM");
                image.setSeriesDescription(namedTag(seriesDicom, "SeriesDescription"));
                image.setModality(namedTag(seriesDicom, "Modality"));
                image.setBodyPart(namedTag(seriesDicom, "BodyPartExamined"));

                // Instance level — iterate ALL instances to collect every SOP UID and frame ID.
                // For batch uploads the imageSet contains N instances (one per slice); we need
                // all of them sorted by InstanceNumber so the viewer renders slices in order.
                JsonNode instancesMap = seriesEntry.getValue().path("Instances");

                // sopUid → {instanceNumber, firstFrameId} — collected across all instances
                record SopInfo(int instanceNumber, String frameId) {}
                Map<String, SopInfo> allInstances = new LinkedHashMap<>();

                instancesMap.fields().forEachRemaining(instanceEntry -> {
                    String sopUid = instanceEntry.getKey();
                    JsonNode instanceDicom = instanceEntry.getValue().path("DICOM");

                    int instanceNum = Integer.MAX_VALUE;
                    String numStr = namedTag(instanceDicom, "InstanceNumber");
                    if (numStr != null) {
                        try { instanceNum = Integer.parseInt(numStr.trim()); }
                        catch (NumberFormatException ignored) {}
                    }

                    JsonNode frames = instanceEntry.getValue().path("ImageFrames");
                    String firstFrameId = (frames.isArray() && !frames.isEmpty())
                            ? frames.get(0).path("ID").asText(null) : null;

                    allInstances.put(sopUid, new SopInfo(instanceNum, firstFrameId));
                });

                // Sort by InstanceNumber ascending
                List<Map.Entry<String, SopInfo>> sortedInstances = allInstances.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.comparingInt(SopInfo::instanceNumber)))
                        .collect(Collectors.toList());

                if (!sortedInstances.isEmpty()) {
                    // Populate legacy single-instance fields from the first (lowest InstanceNumber) SOP
                    Map.Entry<String, SopInfo> first = sortedInstances.get(0);
                    image.setSopInstanceUid(first.getKey());
                    image.setInstanceNumber(first.getValue().instanceNumber() == Integer.MAX_VALUE
                            ? null : first.getValue().instanceNumber());
                    if (first.getValue().frameId() != null) {
                        image.setImageFrameId(first.getValue().frameId());
                    }

                    // Build ordered SOP list and per-SOP frame map for all instances
                    List<String> sopUidList = sortedInstances.stream()
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());
                    Map<String, String> sopFrameMapData = new LinkedHashMap<>();
                    List<String> allFrameIds = new ArrayList<>();
                    for (Map.Entry<String, SopInfo> entry : sortedInstances) {
                        if (entry.getValue().frameId() != null) {
                            sopFrameMapData.put(entry.getKey(), entry.getValue().frameId());
                            allFrameIds.add(entry.getValue().frameId());
                        }
                    }

                    image.setSopInstanceUids(objectMapper.writeValueAsString(sopUidList));
                    if (!sopFrameMapData.isEmpty()) {
                        image.setSopFrameMap(objectMapper.writeValueAsString(sopFrameMapData));
                    }
                    if (!allFrameIds.isEmpty()) {
                        image.setFrameIds(objectMapper.writeValueAsString(allFrameIds));
                        image.setFrameCount(allFrameIds.size());
                    }

                    log.debug("imageSet {} has {} instances",
                            image.getImageSetId(), sortedInstances.size());
                }
            }

            log.debug("Populated metadata for imageSet {}: study={}, series={}, sop={}, frames={}",
                    image.getImageSetId(), image.getStudyInstanceUid(),
                    image.getSeriesInstanceUid(), image.getSopInstanceUid(), image.getFrameCount());

        } catch (Exception e) {
            log.warn("Could not populate metadata for imageSet {}: {}",
                    image.getImageSetId(), e.getMessage());
        }
    }

    /**
     * Streams an image frame from HealthImaging directly to the given output stream.
     */
    public void streamImageFrame(String imageSetId, String imageFrameId, OutputStream out) throws IOException {
        GetImageFrameRequest request = GetImageFrameRequest.builder()
                .datastoreId(datastoreId)
                .imageSetId(imageSetId)
                .imageFrameInformation(ImageFrameInformation.builder()
                        .imageFrameId(imageFrameId)
                        .build())
                .build();

        try (ResponseInputStream<GetImageFrameResponse> response =
                     medicalImagingClient.getImageFrame(request)) {
            response.transferTo(out);
        }
    }

    /**
     * Returns the HealthImaging frame ID for a specific SOP instance and 1-based frame number.
     * Used by the WADO-RS controller when the requested sopUID belongs to a batch record where
     * multiple SOP instances are stored in one DB row (sop_frame_map).
     *
     * Falls back to {@link #resolveFrameId} for legacy single-SOP records.
     */
    public String resolveFrameIdForSop(DicomImage image, String sopUid, int frameNumber) {
        if (image.getSopFrameMap() != null) {
            try {
                Map<String, String> frameMap = objectMapper.readValue(
                        image.getSopFrameMap(), new TypeReference<Map<String, String>>() {});
                if (frameMap.containsKey(sopUid)) {
                    return frameMap.get(sopUid); // single-frame-per-SOP (typical for MR slices)
                }
            } catch (Exception e) {
                log.warn("Failed to parse sop_frame_map for image {}: {}", image.getId(), e.getMessage());
            }
        }
        // Fallback: legacy single-SOP record
        return resolveFrameId(image, frameNumber);
    }

    /**
     * Returns the HealthImaging frame ID for the requested 1-based frame number.
     * Prefers the ordered {@code frame_ids} JSON array; falls back to the legacy
     * {@code image_frame_id} field for frame 1 so that older records still work.
     * Returns {@code null} if the frame number is out of range or no frame data exists.
     */
    public String resolveFrameId(DicomImage image, int frameNumber) {
        if (image.getFrameIds() != null) {
            try {
                List<String> ids = objectMapper.readValue(image.getFrameIds(), new TypeReference<List<String>>() {});
                if (frameNumber >= 1 && frameNumber <= ids.size()) {
                    return ids.get(frameNumber - 1);
                }
                return null;
            } catch (Exception e) {
                log.warn("Failed to parse frame_ids for image {}: {}", image.getId(), e.getMessage());
            }
        }
        // Legacy fallback: single-frame records that pre-date the frame_ids column
        return (frameNumber == 1) ? image.getImageFrameId() : null;
    }

    private byte[] decompressGzip(InputStream gzipStream) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(gzipStream);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            gis.transferTo(baos);
            return baos.toByteArray();
        }
    }

    /**
     * Reads a plain string value from HealthImaging's named-keyword DICOM JSON.
     * HealthImaging stores attributes as { "StudyInstanceUID": "1.2.3...", "Rows": 512 }
     * (plain values, no vr/Value wrapper). Returns null for missing or JSON-null nodes.
     */
    private String namedTag(JsonNode node, String keyword) {
        JsonNode valueNode = node.path(keyword);
        if (valueNode.isMissingNode() || valueNode.isNull()) return null;
        return valueNode.asText(null);
    }

    private LocalDate parseDicomDate(String dicomDate) {
        try {
            // DICOM date format: YYYYMMDD
            return LocalDate.parse(dicomDate, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            log.warn("Could not parse DICOM date '{}': {}", dicomDate, e.getMessage());
            return null;
        }
    }

    /**
     * Reads the HealthImaging output manifest from S3 to extract the image set ID created by
     * a completed import job.
     * After a successful import, HealthImaging writes a manifest file to S3 at
     * {outputS3Uri}job-output-manifest.json. This method reads that file and pulls out
     * the imageSetId so it can be saved to the database.
     * The manifest looks like: {"imageSetsSummary": [{"imageSetId": "..."}]}
     * NOTE: Double-check the exact file path and JSON structure against the AWS HealthImaging docs
     * before relying on this in production.
     *
     * @param outputS3Uri the S3 URI prefix written to the StartDICOMImportJob request
     * @return the first image set ID found in the manifest, or null if it cannot be read
     */
    private String extractImageSetId(String outputS3Uri) {
        URI uri = URI.create(outputS3Uri);
        String bucket = uri.getHost();
        String keyPrefix = uri.getPath().substring(1); // strip leading '/'
        String manifestKey = keyPrefix + "job-output-manifest.json";

        try {
            byte[] bytes = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(manifestKey).build()
            ).asByteArray();

            JsonNode root = objectMapper.readTree(bytes);

            // Try known HealthImaging manifest structures
            String imageSetId = null;

            // Structure 1: { "imageSetsSummary": [{ "imageSetId": "..." }] }
            JsonNode summary = root.path("imageSetsSummary");
            if (summary.isArray() && summary.size() > 0) {
                imageSetId = summary.path(0).path("imageSetId").asText(null);
            }

            // Structure 2: { "jobSummary": { ... }, "imageSetsSummary": [...] } — same as above but nested
            if (imageSetId == null) {
                JsonNode altSummary = root.path("jobSummary").path("imageSetsSummary");
                if (altSummary.isArray() && altSummary.size() > 0) {
                    imageSetId = altSummary.path(0).path("imageSetId").asText(null);
                }
            }

            // Structure 3: flat list at root named "imageSets"
            if (imageSetId == null) {
                JsonNode imageSets = root.path("imageSets");
                if (imageSets.isArray() && imageSets.size() > 0) {
                    imageSetId = imageSets.path(0).path("imageSetId").asText(null);
                }
            }

            if (imageSetId != null) {
                log.debug("Extracted imageSetId {} from manifest at {}", imageSetId, manifestKey);
            } else {
                log.warn("imageSetId not found in manifest at {}. Manifest content: {}",
                        manifestKey, new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            }
            return imageSetId;
        } catch (Exception e) {
            log.warn("Could not read imageSetId from HealthImaging output manifest at {}: {}",
                    outputS3Uri, e.getMessage());
            return null;
        }
    }
}

package com.green.imagecore.entities;

public enum ImportStatus {
    PENDING,     // record created, job not yet submitted to HealthImaging
    SUBMITTED,   // StartDICOMImportJob called, waiting for HealthImaging to begin
    IN_PROGRESS, // HealthImaging is actively processing the DICOM files
    COMPLETED,   // import finished, imageSetId is populated
    FAILED       // import failed; PHI and image data were not ingested
}

-- Add file_count to track how many DICOM instances are in a series upload.
-- s3_key now semantically stores the S3 prefix (folder), e.g. dicom/{userId}/{batchId}/
-- rather than a single file path. Existing rows default to 1 (each was a single file).
ALTER TABLE dicom_images
    ADD COLUMN file_count INTEGER NOT NULL DEFAULT 1;

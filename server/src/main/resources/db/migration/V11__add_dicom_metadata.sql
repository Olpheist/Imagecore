ALTER TABLE dicom_images
    ADD COLUMN study_instance_uid   text,
    ADD COLUMN series_instance_uid  text,
    ADD COLUMN sop_instance_uid     text,
    ADD COLUMN study_description    text,
    ADD COLUMN series_description   text,
    ADD COLUMN body_part            varchar(64),
    ADD COLUMN modality             varchar(16),
    ADD COLUMN patient_id           varchar(64),
    ADD COLUMN study_date           date,
    ADD COLUMN physician            varchar(255),
    ADD COLUMN frame_count          integer NOT NULL DEFAULT 1,
    ADD COLUMN image_frame_id       text,
    -- instance_number: DICOM tag 00200013, used to sort slices within a series
    --   when a series is spread across multiple DB records (one upload per slice).
    ADD COLUMN instance_number      integer,
    -- frame_ids: ordered JSON array of HealthImaging frame IDs for all frames in
    --   this image set, enabling correct frame retrieval for multi-frame DICOMs.
    ADD COLUMN frame_ids            text,
    -- sop_instance_uids: ordered JSON array of all SOP instance UIDs in this image set,
    --   sorted by InstanceNumber. For batch uploads this stores all N slice UIDs in one row.
    ADD COLUMN sop_instance_uids    text,
    -- sop_frame_map: JSON object {sopUid: frameId} mapping each SOP to its HealthImaging
    --   frame ID, enabling per-slice frame retrieval for batch-uploaded series.
    ADD COLUMN sop_frame_map        text;

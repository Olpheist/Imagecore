create table if not exists dicom_images (
    id                      bigserial primary key,
    user_id                 bigint not null,
    s3_key                  varchar(512) not null,
    filename                varchar(255) not null,
    file_size               bigint not null,
    health_imaging_job_id   text,
    image_set_id            text,
    import_status           varchar(32) not null default 'PENDING',
    uploaded_at             timestamptz not null default now(),
    constraint fk_dicom_images_user foreign key (user_id) references users(id) on delete cascade
);
create index idx_dicom_images_user_id on dicom_images (user_id);

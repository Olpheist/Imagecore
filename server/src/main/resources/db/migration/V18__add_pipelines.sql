create table if not exists pipelines (
    id          bigserial primary key,
    image_id    bigint not null references dicom_images(id) on delete cascade,
    user_id     bigint not null references users(id) on delete cascade,
    status      varchar(32) not null default 'PENDING',
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);
create index idx_pipelines_user_id  on pipelines (user_id);
create index idx_pipelines_image_id on pipelines (image_id);

create table if not exists pipeline_steps (
    id              bigserial primary key,
    pipeline_id     bigint not null references pipelines(id) on delete cascade,
    step_order      int not null,
    tool_id         bigint not null references tools(tool_id) on delete restrict,
    analysis_job_id bigint references analysis_jobs(id) on delete set null,
    output_image_id bigint references dicom_images(id) on delete set null,
    status          varchar(32) not null default 'PENDING',
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);
create index idx_pipeline_steps_pipeline_id     on pipeline_steps (pipeline_id);
create index idx_pipeline_steps_analysis_job_id on pipeline_steps (analysis_job_id);

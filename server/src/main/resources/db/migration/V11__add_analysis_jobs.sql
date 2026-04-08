create table if not exists analysis_jobs (
    id              bigserial primary key,
    image_id        bigint not null references dicom_images(id) on delete cascade,
    tool_id         bigint not null references tools(tool_id) on delete restrict,
    user_id         bigint not null references users(id) on delete cascade,
    status          varchar(32) not null default 'PENDING',
    ecs_task_arn    text,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);
create index idx_analysis_jobs_user_id  on analysis_jobs (user_id);
create index idx_analysis_jobs_image_id on analysis_jobs (image_id);

alter table tools
    add column if not exists task_definition_arn text,
    add column if not exists container_name      varchar(255) not null default 'app';

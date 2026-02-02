create table users (
    id bigserial primary key,
    email varchar(320) not null unique,
    password_hash text not null,
    is_enabled boolean not null default true,
    created_at timestamptz not null default now()
);

create table roles (
    id bigserial primary key,
    name varchar(64) not null unique
);

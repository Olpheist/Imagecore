create table if not exists permissions (
    id bigserial primary key,
    name varchar(128) not null unique
);

create table if not exists user_roles (
    user_id bigint not null,
    role_id bigint not null,
    primary key (user_id, role_id),
    foreign key (user_id) references users(id) on delete cascade,
    foreign key (role_id) references roles(id) on delete cascade
);

create index if not exists idx_user_roles_user_id on user_roles(user_id);
create index if not exists idx_user_roles_role_id on user_roles(role_id);

create table if not exists role_permissions (
    role_id bigint not null,
    permission_id bigint not null,
    primary key (role_id, permission_id),
    foreign key (role_id) references roles(id) on delete cascade,
    foreign key (permission_id) references permissions(id) on delete cascade
);

create index if not exists idx_role_permissions_role_id on role_permissions(role_id);
create index if not exists idx_role_permissions_permission_id on role_permissions(permission_id);

alter table users
add column username varchar(100);

insert into roles (name) values
  ('CLINICIAN'),
  ('RESEARCHER'),
  ('TECHNICIAN'),
  ('PATIENT'),
  ('ADMIN')
on conflict (name) do nothing;

insert into permissions (name) values
 ('UPLOAD'),
 ('VIEW'),
 ('PHI'),
 ('RESULTS')
on conflict (name) do nothing;

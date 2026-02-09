alter table users
alter column username set not null;

alter table users
add constraint users_username_unique unique (username);

update permissions
set name = 'IMAGE_UPLOAD'
where name = 'UPLOAD';

update permissions
set name = 'IMAGE_VIEW'
where name = 'VIEW';

update permissions
set name = 'PHI_VIEW'
where name = 'PHI';

update permissions
set name = 'RESULTS_VIEW'
where name = 'RESULTS';

insert into permissions (name)
values ('TOOL_EXEC')
    on conflict (name) do nothing;
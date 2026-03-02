-- DEV/LOCAL SEED ONLY — do not apply to production
-- Creates one test user per role. Password for all users is: password

insert into users (email, username, password_hash, is_enabled)
values
    ('clinician@dev.local',  'clinician',  '$2b$10$v7Qf501uqnJSQgLNAEKV6O1Z6i0qdhDiAGr4LTNSsdQJr2EeD2GVa', true),
    ('researcher@dev.local', 'researcher', '$2b$10$.4YWfM7hsr8KwRHh15Q0M.I3ZupfJZWsBG9a4pZeitrh8cE/y2pEm', true),
    ('technician@dev.local', 'technician', '$2b$10$/VyBBDKaqpBV0cObHfzdq.VKwPO7XC1/zjHVX8PVLAyNbyBOXA2OK', true),
    ('patient@dev.local',    'patient',    '$2b$10$vpwnQEFLRctpmXQkMGvHTOZwYj1NkpA0v9irUe.DDBYLdlhCE6bY2', true),
    ('admin@dev.local',      'admin',      '$2b$10$iUk5llpNcRRzBIooaeUXsey7hSoTuK5iSiv29ZZCkq36jmPoAbXE2', true)
on conflict (username) do nothing;

with role_mapping (username, role_name) as (
    values
        ('clinician',  'CLINICIAN'),
        ('researcher', 'RESEARCHER'),
        ('technician', 'TECHNICIAN'),
        ('patient',    'PATIENT'),
        ('admin',      'ADMIN')
)
insert into user_roles (user_id, role_id)
select u.id, r.id
from role_mapping m
join users u on u.username = m.username
join roles  r on r.name    = m.role_name
on conflict do nothing;

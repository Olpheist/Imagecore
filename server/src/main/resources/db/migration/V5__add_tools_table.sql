create table if not exists tools (
    tool_id bigserial primary key,
    created_by_user_id bigint not null,
    name varchar(64) not null unique,
    category varchar(64) not null,
    description text,
    image_tag varchar(150),
    foreign key (created_by_user_id) references users(id)
    );

insert into tools (tool_id, created_by_user_id, name, category, description, image_tag)
values (1, 2, 'Image Slice', 'Reporting', 'Simple tool that takes in a DICOM volume and creates a PDF report with metadata and a middle slice of the image', '259950038280.dkr.ecr.us-east-1.amazonaws.com/imagecore-dev-tools:image-pipeline-latest');

-- Reset the sequence so new inserts don't conflict with manually seeded IDs
SELECT setval('tools_tool_id_seq', (SELECT MAX(tool_id) FROM tools));

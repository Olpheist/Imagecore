-- Seed the N4 Bias Field Correction tool.
-- required_tier_id references the FREE tier seeded in V12__subscriptions.sql
insert into tools (tool_id, created_by_user_id, name, category, description, image_tag, task_definition_arn, container_name, required_tier_id)
values (2, 2, 'N4 Bias Field Correction', 'preprocessing', 'Corrects MRI intensity non-uniformity (bias field) using the N4ITK algorithm. Reads from HealthImaging, applies correction, and reimports the corrected series.', '259950038280.dkr.ecr.us-east-1.amazonaws.com/imagecore-dev-tools:n4-bias-correction-latest', 'arn:aws:ecs:us-east-1:259950038280:task-definition/imagecore-dev-n4-bias-correction', 'app', (SELECT id FROM subscription_tiers WHERE code = 'FREE'));

-- Reset the sequence so new inserts don't conflict with manually seeded IDs
SELECT setval('tools_tool_id_seq', (SELECT MAX(tool_id) FROM tools));

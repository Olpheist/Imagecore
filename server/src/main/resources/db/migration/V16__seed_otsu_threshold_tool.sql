-- Seed the Otsu Threshold segmentation tool.
-- task_definition_arn is a placeholder; update after pushing the image to ECR
-- and registering the ECS task definition.
insert into tools (created_by_user_id, name, category, description, image_tag, task_definition_arn, container_name, required_tier_id)
values (2, 'Otsu Threshold', 'segmentation', 'Segments MRI volumes using Otsu thresholding. Produces a masked intensity image and a binary mask, both reimported into HealthImaging. Intended to run after N4 bias field correction.', '259950038280.dkr.ecr.us-east-1.amazonaws.com/imagecore-dev-tools:otsu-threshold-latest', 'arn:aws:ecs:us-east-1:259950038280:task-definition/imagecore-dev-otsu-threshold', 'app', 1);

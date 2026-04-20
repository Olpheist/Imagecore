-- Seed the Otsu Threshold segmentation tool.
-- task_definition_arn is a placeholder; update after pushing the image to ECR
-- and registering the ECS task definition.
INSERT INTO tools (name, category, task_definition_arn, created_at)
VALUES (
    'Otsu Threshold',
    'segmentation',
    'arn:aws:ecs:us-east-1:259950038280:task-definition/otsu-threshold:1',
    NOW()
);

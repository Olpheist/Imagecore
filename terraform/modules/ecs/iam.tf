# ECS Task Execution Role (for pulling images and writing logs)
resource "aws_iam_role" "ecs_execution" {
  name = "${var.project_name}-${var.environment}-ecs-execution-role"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
      "Condition": {
        "ArnLike" : {
          "aws:SourceArn" : "arn:aws:ecs:us-east-1:259950038280:*"
        },
        "StringEquals" : {
          "aws:SourceAccount" : "259950038280"
        }
      }
    }]
  })
}

# Attach AWS managed policy for ECS task execution
resource "aws_iam_role_policy_attachment" "ecs_execution" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Additional policy for ECR access
resource "aws_iam_role_policy" "ecs_execution_ecr" {
  name = "${var.project_name}-${var.environment}-ecs-execution-ecr-policy"
  role = aws_iam_role.ecs_execution.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken",
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage"
        ]
        Resource = "*"
      }
    ]
  })
}

# ECS Task Role (for application permissions)
resource "aws_iam_role" "ecs_task" {
  name = "${var.project_name}-${var.environment}-ecs-task-role"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })
}

# Policy for task role (add application-specific permissions here)
resource "aws_iam_role_policy" "ecs_task" {
  name = "${var.project_name}-${var.environment}-ecs-task-policy"
  role = aws_iam_role.ecs_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "s3:PutObject",     #upload files to S3
          "s3:GetObject",     #download files from S3
          "s3:DeleteObject",  #remove files from S3
          "s3:ListBucket"     #view files in the bucket
        ]
        Resource = [
          var.s3_bucket_arn,        #bucket
          "${var.s3_bucket_arn}/*"  #objects in bucket
        ]
      },
      {
        Sid    = "HealthImagingOperations"
        Effect = "Allow"
        Action = [
          "medical-imaging:StartDICOMImportJob",   #trigger a new import from S3 into the datastore
          "medical-imaging:GetDICOMImportJob",     #check the status of a running or completed import job
          "medical-imaging:ListDICOMImportJobs",   #list all import jobs for a datastore
          "medical-imaging:GetImageSet",           #retrieve metadata for an imported image set
          "medical-imaging:GetImageSetMetadata",   #retrieve DICOM metadata blob for an image set (spatial info, frame IDs)
          "medical-imaging:SearchImageSets",       #query image sets by patient/study attributes
          "medical-imaging:ListImageSetVersions",  #list versions of an image set (HealthImaging is immutable; updates create new versions)
          "medical-imaging:GetImageFrame",         #retrieve individual image frames for WADO-RS serving
          "medical-imaging:DeleteImageSet"         #remove an image set from the datastore
        ]
        Resource = [
          var.health_imaging_datastore_arn,               #datastore-level operations (e.g. StartDICOMImportJob, ListDICOMImportJobs)
          "${var.health_imaging_datastore_arn}/imageset/*" #image set-level operations (e.g. GetImageSet, GetImageFrame)
        ]
      },
      {
        Sid    = "PassHealthImagingImportRole"
        Effect = "Allow"
        Action = ["iam:PassRole"]
        Resource = var.health_imaging_import_role_arn  #the role HealthImaging assumes to read/write S3 during import
        Condition = {
          StringEquals = {
            "iam:PassedToService" = "medical-imaging.amazonaws.com"  #restricts pass to HealthImaging only, preventing privilege escalation
          }
        }
      },
      {
        Sid    = "RunAnalysisTools"
        Effect = "Allow"
        Action = [
          "ecs:RunTask",
          "ecs:DescribeTasks",  #required to poll analysis tool task status for job completion detection
          "iam:PassRole"  #required to pass execution and task roles when launching tool tasks
        ]
        Resource = "*"
      }
    ]
  })
}

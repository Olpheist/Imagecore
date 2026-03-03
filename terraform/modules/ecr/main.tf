# ECR Repository
resource "aws_ecr_repository" "main" {
  name                 = "${var.project_name}-${var.environment}-${var.repository_name}"
  image_tag_mutability = "MUTABLE"
  
  image_scanning_configuration {
    scan_on_push = true
  }
  
  encryption_configuration {
    encryption_type = "AES256"  # TODO: look into aws KMS
  }
  
  tags = {
    Name = "${var.project_name}-${var.environment}-${var.repository_name}"
  }
}

# ECR Lifecycle Policy (keeps last 5 images)
resource "aws_ecr_lifecycle_policy" "main" {
  repository = aws_ecr_repository.main.name
  
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 5 images"
      selection = {
        tagStatus     = "any"
        countType     = "imageCountMoreThan"
        countNumber   = 5
      }
      action = {
        type = "expire"
      }
    }]
  })
}

# ECR Tools Repository
resource "aws_ecr_repository" "tools" {
  name                 = "${var.project_name}-${var.environment}-tools"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Name = "${var.project_name}-${var.environment}-tools"
  }
}

resource "aws_ecr_lifecycle_policy" "tools" {
  repository = aws_ecr_repository.tools.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after 7 days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 7
        }
        action = { type = "expire" }
      }
    ]
  })
}
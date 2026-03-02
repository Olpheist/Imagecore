output "repository_url" {
  description = "URL of the ECR repository"
  value       = aws_ecr_repository.main.repository_url
}

output "repository_arn" {
  description = "ARN of the ECR repository"
  value       = aws_ecr_repository.main.arn
}

output "repository_name" {
  description = "Name of the ECR repository"
  value       = aws_ecr_repository.main.name
}

output "tools_repository_url" {
  description = "Shared tools ECR repository URL."
  value       = aws_ecr_repository.tools.repository_url
}

output "tools_repository_arn" {
  description = "Shared tools ECR repository ARN."
  value       = aws_ecr_repository.tools.arn
}
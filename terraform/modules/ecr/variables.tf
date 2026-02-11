variable "project_name" {
  description = "Project name for resource naming"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "repository_name" {
  description = "Name of the ECR repository"
  type        = string
}

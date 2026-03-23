variable "project_name" {
  description = "Project name for resource naming"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "health_imaging_import_role_arn" {
  description = "ARN of the HealthImaging import IAM role, granted S3 read/write for import jobs"
  type        = string
  default     = null
}

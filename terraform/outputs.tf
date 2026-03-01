output "ecr_repository_url" {
  description = "URL of the ECR repository"
  value       = module.ecr.repository_url
}

output "vpc_id" {
  description = "ID of the VPC"
  value       = module.networking.vpc_id
}

output "ecs_cluster_name" {
  description = "Name of the ECS cluster"
  value       = module.ecs.cluster_name
}

output "ecs_service_name" {
  description = "Name of the ECS service"
  value       = module.ecs.service_name
}

output "alb_dns_name" {
  description = "DNS name of the Application Load Balancer"
  value       = module.ecs.alb_dns_name
}

output "db_endpoint" {
  description = "RDS database endpoint"
  value       = module.rds.db_endpoint
  sensitive   = true
}

output "db_name" {
  description = "RDS database name"
  value       = module.rds.db_name
}

output "s3_bucket_name" {
  description = "Name of the DICOM S3 bucket"
  value       = module.s3.bucket_name
}

output "s3_kms_key_arn" {
  description = "ARN of the KMS key used to encrypt the DICOM bucket"
  value       = module.s3.kms_key_arn
  sensitive   = true
}

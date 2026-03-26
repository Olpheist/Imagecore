output "datastore_id" {
  description = "ID of the HealthImaging datastore"
  value       = aws_healthimaging_datastore.main.datastore_id
}

output "datastore_arn" {
  description = "ARN of the HealthImaging datastore"
  value       = aws_healthimaging_datastore.main.arn
}

output "import_role_arn" {
  description = "ARN of the IAM role used by HealthImaging during S3 import jobs"
  value       = aws_iam_role.import.arn
}

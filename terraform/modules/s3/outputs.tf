output "bucket_name" {
  description = "Name of the DICOM S3 bucket"
  value       = aws_s3_bucket.dicom.id
}

output "bucket_arn" {
  description = "ARN of the DICOM S3 bucket"
  value       = aws_s3_bucket.dicom.arn
}


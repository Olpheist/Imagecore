data "aws_caller_identity" "current" {}

# S3 Bucket for DICOM images
# Note: Using the AWS-managed KMS key (aws/s3) to avoid the ~$1/month CMK fee.
# A Customer-Managed KMS Key would be more secure (key rotation control, resource policies,
# CloudTrail visibility per-key), but is unnecessary for our project
resource "aws_s3_bucket" "dicom" {
  bucket        = "${var.project_name}-${var.environment}-dicom-${data.aws_caller_identity.current.account_id}"
  force_destroy = false

  lifecycle {
    prevent_destroy = true
  }

}

resource "aws_s3_bucket_public_access_block" "dicom" {
  bucket = aws_s3_bucket.dicom.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "dicom" {
  bucket = aws_s3_bucket.dicom.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "dicom" {
  bucket = aws_s3_bucket.dicom.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "aws:kms"
      # aws/s3 is the AWS-managed key; no kms_master_key_id needed to use it
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_ownership_controls" "dicom" {
  bucket = aws_s3_bucket.dicom.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_policy" "dicom" {
  bucket = aws_s3_bucket.dicom.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "DenyHTTP"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource = [
          aws_s3_bucket.dicom.arn,
          "${aws_s3_bucket.dicom.arn}/*"
        ]
        Condition = {
          Bool = {
            "aws:SecureTransport" = "false"
          }
        }
      }
    ]
  })

  depends_on = [aws_s3_bucket_public_access_block.dicom]
}

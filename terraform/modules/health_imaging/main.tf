data "aws_caller_identity" "current" {}

# AWS HealthImaging Datastore
resource "awscc_healthimaging_datastore" "main" {
  datastore_name = "${var.project_name}-${var.environment}"
}

# IAM role that AWS HealthImaging assumes during DICOM import jobs
resource "aws_iam_role" "import" {
  name = "${var.project_name}-${var.environment}-health-imaging-import-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "medical-imaging.amazonaws.com" }
      Action    = "sts:AssumeRole"
      Condition = {
        StringEquals = {
          "aws:SourceAccount" = data.aws_caller_identity.current.account_id
        }
      }
    }]
  })
}

# Grant the import role read access to DICOM input files and write access for job output
resource "aws_iam_role_policy" "import_s3" {
  name = "${var.project_name}-${var.environment}-health-imaging-import-s3-policy"
  role = aws_iam_role.import.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ReadDicomInput"
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:ListBucket"
        ]
        Resource = [
          var.s3_bucket_arn,
          "${var.s3_bucket_arn}/dicom/*"
        ]
      },
      {
        Sid      = "WriteImportOutput"
        Effect   = "Allow"
        Action   = ["s3:PutObject"]
        Resource = "${var.s3_bucket_arn}/health-imaging-output/*"
      }
    ]
  })
}

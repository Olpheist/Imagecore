provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}

provider "awscc" {
  region = var.aws_region
}

# ECR Module - Container Registry
module "ecr" {
  source = "./modules/ecr"

  project_name    = var.project_name
  environment     = var.environment
  repository_name = var.ecr_repository_name
}

# Networking Module - VPC, Subnets, Security Groups
module "networking" {
  source = "./modules/networking"

  project_name         = var.project_name
  environment          = var.environment
  vpc_cidr             = var.vpc_cidr
  availability_zones   = var.availability_zones
  public_subnet_cidrs  = var.public_subnet_cidrs
  private_subnet_cidrs = var.private_subnet_cidrs
}

# RDS Module - Database
module "rds" {
  source = "./modules/rds"
  # postgres db engine is the default for this infra

  project_name          = var.project_name
  environment           = var.environment
  vpc_id                = module.networking.vpc_id
  private_subnet_ids    = module.networking.private_subnet_ids
  app_security_group_id = module.networking.app_security_group_id

  db_name           = var.db_name
  db_username       = var.db_username
  db_password       = var.db_password
  db_instance_class = var.db_instance_class
  allocated_storage = var.db_allocated_storage
}


# S3 Module - DICOM Image Storage
module "s3" {
  source = "./modules/s3"

  project_name                   = var.project_name
  environment                    = var.environment
  health_imaging_import_role_arn = module.health_imaging.import_role_arn
}

# HealthImaging Module - DICOM Import and Image Set Management
module "health_imaging" {
  source = "./modules/health_imaging"

  project_name  = var.project_name
  environment   = var.environment
  s3_bucket_arn = module.s3.bucket_arn
}

# ECS Module - Container Orchestration
module "ecs" {
  source = "./modules/ecs"

  project_name          = var.project_name
  environment           = var.environment
  vpc_id                = module.networking.vpc_id
  public_subnet_ids     = module.networking.public_subnet_ids
  private_subnet_ids    = module.networking.private_subnet_ids
  app_security_group_id = module.networking.app_security_group_id
  alb_security_group_id = module.networking.alb_security_group_id

  ecr_repository_url = module.ecr.repository_url
  image_tag          = var.image_tag
  container_port     = var.container_port
  desired_count      = var.ecs_desired_count

  # Database connection info
  db_host     = module.rds.db_address
  db_name     = var.db_name
  db_username = var.db_username
  db_password = var.db_password

  # S3
  s3_bucket_name = module.s3.bucket_name
  s3_bucket_arn  = module.s3.bucket_arn

  # HealthImaging
  health_imaging_datastore_id    = module.health_imaging.datastore_id
  health_imaging_datastore_arn   = module.health_imaging.datastore_arn
  health_imaging_import_role_arn = module.health_imaging.import_role_arn

  # SendGrid
  send_grid_password = var.send_grid_password

}

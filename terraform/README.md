# AWS Infrastructure with Terraform

This repository contains Terraform infrastructure-as-code for deploying a containerized application on AWS using ECS, RDS, and supporting services.

## Architecture Overview

- **ECR (Elastic Container Registry)**: Stores Docker container images
- **ECS (Elastic Container Service)**: Orchestrates containers on EC2 instances
- **EC2 Auto Scaling Group**: Provides compute capacity for ECS cluster
- **Application Load Balancer**: Distributes traffic to ECS tasks
- **RDS (Relational Database System)**: Managed Postgres database with encryption at rest
- **VPC (Virtual Private Cloud)**: Isolated network with public/private subnets
- **Security Groups**: Network-level security controls

### Common Abbreviations

* **cidr:** Classless Inter-Domain Routing
* **alb:** Application Load Balancer

## Directory Structure

```
terraform/
├── main.tf                 # Root module orchestration
├── variables.tf            # Input variables
├── outputs.tf              # Output values
├── backend.tf              # State backend configuration
├── terraform.tfvars.example # Example variable values
├── modules/
│   ├── networking/         # VPC, subnets, security groups
│   ├── ecr/                # Container registry
│   ├── ecs/                # Container orchestration
│   ├── ec2/                # ECS cluster instances
│   └── rds/                # Database
└── environments/           # Environment-specific configs (future)
```

## Prerequisites

1. **AWS Account** with appropriate permissions
2. **AWS CLI** configured with credentials
3. **Terraform** >= 1.0 installed
4. **Docker** for building container images

## Getting Started

### 1. Initialize Terraform

```bash
cp terraform.tfvars.example terraform.tfvars # DO NOT ADD terraform.tfvars to github

terraform init
```

### 2. Review the Plan

```bash
terraform plan
```

### 3. Deploy Infrastructure

```bash
terraform apply
```

Review the output and type `yes` to confirm.

### 4. Build and Push Container

The deployment script (`scripts/push_docker_image_to_ecr.sh`) automates the following steps:

> [!NOTE]
> The image tag in this script should match the `image_tag` variable in the `terraform.tfvars` file

1. **Build** the Docker image for the target platform
2. **Authenticate** with AWS ECR
3. **Tag** the image with the ECR repository URL
4. **Push** the image to ECR
5. **Verify** the image was uploaded successfully (optional)

## Configuration

### Required Variables

Edit `terraform.tfvars` with these required values:

```hcl
project_name = "imagecore"
environment  = "dev"
db_password  = "SECURE_PASSWORD_HERE"
```

### Optional Variables

See `variables.tf` for all configurable options including:
- Instance types
- Database configuration
- Network CIDR blocks
- Scaling parameters

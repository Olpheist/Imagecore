#!/bin/bash
set -e

IMAGE_TAG=${IMAGE_TAG:-"latest"}
ECR_REGISTRY="259950038280.dkr.ecr.us-east-1.amazonaws.com"
ECR_REPO="imagecore-dev-tools"
IMAGE_NAME="otsu-threshold"
FULL_URI="${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_NAME}-${IMAGE_TAG}"

# Authenticate with ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin "${ECR_REGISTRY}"

# Build the image from the otsu-threshold tool directory
docker buildx build \
  --platform linux/amd64 \
  -f tools/otsu-threshold/Dockerfile_otsu-threshold \
  -t "${IMAGE_NAME}:${IMAGE_TAG}" \
  tools/otsu-threshold

# Tag for ECR
docker tag "${IMAGE_NAME}:${IMAGE_TAG}" "${FULL_URI}"

# Push to ECR
docker push "${FULL_URI}"

echo ""
echo "Successfully pushed to ECR:"
echo "  ${FULL_URI}"

# Verify in ECR
aws ecr describe-images \
  --repository-name "${ECR_REPO}" \
  --region us-east-1 \
  --query 'imageDetails[?contains(imageTags, `otsu-threshold-latest`)]' \
  --output table

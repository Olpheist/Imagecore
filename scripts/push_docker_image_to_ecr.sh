# Go into server directory
cd server || exit 1

IMAGE_TAG=${IMAGE_TAG:="v1.0.3"}

# Build for AMD64 (EC2 architecture) TODO: might need to further confirm this
docker buildx build --platform linux/amd64,linux/arm64 -t imagecore-aws:"${IMAGE_TAG}" .

# Authenticate with ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin 259950038280.dkr.ecr.us-east-1.amazonaws.com

# Tag for ECR
docker tag imagecore-aws:"${IMAGE_TAG}" 259950038280.dkr.ecr.us-east-1.amazonaws.com/imagecore-dev-sep-imagecore:"${IMAGE_TAG}"

# Push to ECR
docker push 259950038280.dkr.ecr.us-east-1.amazonaws.com/imagecore-dev-sep-imagecore:"${IMAGE_TAG}"

# Verify in ECR
aws ecr describe-images \
  --repository-name imagecore-dev-sep-imagecore \
  --region us-east-1
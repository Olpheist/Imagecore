#!/bin/bash
# User data script for ECS-optimized EC2 instances
# Script that runs on the instance at launch time

# Configure ECS agent to join the cluster
echo "ECS_CLUSTER=${ecs_cluster_name}" >> /etc/ecs/ecs.config

# TODO: look into install CloudWatch agent for monitoring
# yum install -y amazon-cloudwatch-agent

# TODO: see if this optional additional system configuration is useful
yum update -y

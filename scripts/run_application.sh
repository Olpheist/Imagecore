#!/bin/bash

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }

die() {
  error "$*"
  exit 1
}

# Find repo root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"
info "Working directory: $ROOT_DIR"

# Dependency checks
info "Checking required tools..."

check_tool() {
  local tool=$1
  local install_hint=$2
  if ! command -v "$tool" &>/dev/null; then
    die "'$tool' is not installed or not on PATH. $install_hint"
  fi
  success "$tool found ($(command -v "$tool"))"
}

check_tool "node"          "Install Node.js from https://nodejs.org"
check_tool "npm"           "Install Node.js (includes npm) from https://nodejs.org"
check_tool "docker"        "Install Docker from https://docs.docker.com/get-docker"
check_tool "docker-compose" "Install Docker Compose from https://docs.docker.com/compose/install"

# Verify Docker daemon is actually running
if ! docker info &>/dev/null; then
  die "Docker is installed but the daemon is not running. Please start Docker and try again."
fi

# Frontend build
info "Installing frontend dependencies..."
(cd client && npm ci)

info "Building frontend (npm run generate)..."
(cd client && npm run generate)

# Verify the build output exists before proceeding
if [ ! -d "client/.output" ]; then
  die "Frontend build output not found at client/.output — the build may have failed silently."
fi

# Copy build output to Java static resources
STATIC_DIR="server/src/main/resources/static"

info "Clearing existing static resources..."
rm -rf "${STATIC_DIR:?}"/*

info "Copying frontend build to $STATIC_DIR..."
cp -r client/.output/public/. "$STATIC_DIR/"

success "Frontend build copied successfully."

# Docker Compose
info "Starting application with docker-compose..."
docker-compose -f docker-compose-dev.yml up --build
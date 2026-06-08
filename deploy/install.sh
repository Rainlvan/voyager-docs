#!/usr/bin/env bash
set -euo pipefail

APP_NAME="voyager"
REPO_URL="${REPO_URL:-https://github.com/Rainlvan/voyager-docs.git}"
INSTALL_ROOT="${INSTALL_ROOT:-/opt/voyager}"
APP_DIR="${APP_DIR:-$INSTALL_ROOT/repo}"
GHCR_OWNER="${GHCR_OWNER:-rainlvan}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
DOCKER_MIRROR="${DOCKER_MIRROR:-https://docker.1ms.run}"
DOCKER_CMD="docker"

info() {
  printf '\033[1;34m[INFO]\033[0m %s\n' "$*"
}

warn() {
  printf '\033[1;33m[WARN]\033[0m %s\n' "$*"
}

fail() {
  printf '\033[1;31m[ERROR]\033[0m %s\n' "$*" >&2
  exit 1
}

need_root_command() {
  if [ "$(id -u)" -eq 0 ]; then
    echo ""
  elif command -v sudo >/dev/null 2>&1; then
    echo "sudo"
  else
    fail "This script needs root permissions. Please run as root or install sudo."
  fi
}

ask_yes_no() {
  local prompt="$1"
  local default="${2:-Y}"
  local reply
  local suffix
  if [ "$default" = "Y" ]; then
    suffix="[Y/n]"
  else
    suffix="[y/N]"
  fi
  while true; do
    read -r -p "$prompt $suffix " reply || true
    reply="${reply:-$default}"
    case "$reply" in
      Y|y|YES|yes) return 0 ;;
      N|n|NO|no) return 1 ;;
      *) echo "Please answer y or n." ;;
    esac
  done
}

detect_package_manager() {
  if command -v apt-get >/dev/null 2>&1; then
    echo "apt"
  elif command -v dnf >/dev/null 2>&1; then
    echo "dnf"
  elif command -v yum >/dev/null 2>&1; then
    echo "yum"
  else
    echo ""
  fi
}

install_basic_packages() {
  local sudo_cmd="$1"
  local pm
  pm="$(detect_package_manager)"
  case "$pm" in
    apt)
      $sudo_cmd apt-get update
      $sudo_cmd apt-get install -y git curl openssl ca-certificates
      ;;
    dnf)
      $sudo_cmd dnf install -y git curl openssl ca-certificates
      ;;
    yum)
      $sudo_cmd yum install -y git curl openssl ca-certificates
      ;;
    *)
      warn "Unknown package manager. Please make sure git, curl, and openssl are installed."
      ;;
  esac
}

install_docker() {
  local sudo_cmd="$1"
  info "Installing Docker..."
  curl -fsSL https://get.docker.com | $sudo_cmd sh
  $sudo_cmd systemctl enable --now docker
}

docker_ready() {
  command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1
}

configure_docker_command() {
  local sudo_cmd="$1"
  if docker info >/dev/null 2>&1; then
    DOCKER_CMD="docker"
  elif [ -n "$sudo_cmd" ] && $sudo_cmd docker info >/dev/null 2>&1; then
    DOCKER_CMD="$sudo_cmd docker"
    warn "Docker requires elevated permissions. This script will use: $DOCKER_CMD"
  else
    fail "Docker is installed but not accessible. Please check the Docker service and permissions."
  fi
}

configure_docker_mirror() {
  local sudo_cmd="$1"
  info "Configuring Docker registry mirror: $DOCKER_MIRROR"
  $sudo_cmd mkdir -p /etc/docker
  if [ -f /etc/docker/daemon.json ]; then
    local backup="/etc/docker/daemon.json.voyager-backup-$(date +%Y%m%d%H%M%S)"
    $sudo_cmd cp /etc/docker/daemon.json "$backup"
    warn "Existing /etc/docker/daemon.json backed up to $backup"
  fi
  cat <<JSON | $sudo_cmd tee /etc/docker/daemon.json >/dev/null
{
  "registry-mirrors": ["$DOCKER_MIRROR"]
}
JSON
  $sudo_cmd systemctl restart docker
}

configure_opensearch_kernel() {
  local sudo_cmd="$1"
  info "Configuring vm.max_map_count for OpenSearch..."
  echo 'vm.max_map_count=262144' | $sudo_cmd tee /etc/sysctl.d/99-voyager-opensearch.conf >/dev/null
  $sudo_cmd sysctl --system >/dev/null
}

random_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32
  else
    head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n'
  fi
}

prepare_repo() {
  local sudo_cmd="$1"
  $sudo_cmd mkdir -p "$INSTALL_ROOT"
  $sudo_cmd chown "$(id -u):$(id -g)" "$INSTALL_ROOT"

  if [ -d "$APP_DIR/.git" ]; then
    info "Repository already exists. Updating: $APP_DIR"
    git -C "$APP_DIR" pull
  else
    info "Cloning Voyager Docs into: $APP_DIR"
    git clone "$REPO_URL" "$APP_DIR"
  fi
}

write_env_file() {
  local env_file="$APP_DIR/.env"
  if [ -f "$env_file" ]; then
    warn "$env_file already exists. Keeping existing production secrets."
    return 0
  fi

  info "Creating production .env with random secrets..."
  cat >"$env_file" <<EOF
GHCR_OWNER=$GHCR_OWNER
IMAGE_TAG=$IMAGE_TAG

POSTGRES_DB=voyager
POSTGRES_USER=voyager
POSTGRES_PASSWORD=$(random_secret)

MINIO_ROOT_USER=voyager_minio
MINIO_ROOT_PASSWORD=$(random_secret)
MINIO_BUCKET=voyager-documents

OPENSEARCH_INITIAL_ADMIN_PASSWORD=$(random_secret)
OPENSEARCH_DOCUMENT_INDEX=voyager-documents

VOYAGER_SECURITY_JWT_SECRET=$(random_secret)
VOYAGER_CRYPTO_SECRET=$(random_secret)

VOYAGER_BOOTSTRAP_ADMIN_USERNAME=admin
VOYAGER_BOOTSTRAP_ADMIN_DISPLAY_NAME=System Administrator
VOYAGER_BOOTSTRAP_ADMIN_PASSWORD=12345678
VOYAGER_BOOTSTRAP_EMPLOYEE_USERNAME=employee
VOYAGER_BOOTSTRAP_EMPLOYEE_DISPLAY_NAME=Employee
VOYAGER_BOOTSTRAP_EMPLOYEE_PASSWORD=12345678

VOYAGER_WORKER_ID=prod-worker-1
EOF
  chmod 600 "$env_file"
}

start_services() {
  cd "$APP_DIR"
  info "Pulling Docker images from GHCR..."
  $DOCKER_CMD compose --env-file .env -f docker-compose.prod.yml pull
  info "Starting Voyager Docs..."
  $DOCKER_CMD compose --env-file .env -f docker-compose.prod.yml up -d --no-build
  $DOCKER_CMD compose --env-file .env -f docker-compose.prod.yml ps
}

main() {
  if [ "$(uname -s)" != "Linux" ]; then
    fail "This installer is intended for Linux servers."
  fi

  local sudo_cmd
  sudo_cmd="$(need_root_command)"

  info "Voyager Docs one-click installer"
  info "Repository: $REPO_URL"
  info "Install directory: $APP_DIR"
  info "Image owner: $GHCR_OWNER"

  install_basic_packages "$sudo_cmd"

  if docker_ready; then
    info "Docker and Docker Compose are already installed."
    if ask_yes_no "Use the existing Docker installation?" "Y"; then
      info "Keeping existing Docker installation."
    else
      install_docker "$sudo_cmd"
    fi
  else
    warn "Docker or Docker Compose was not detected."
    if ask_yes_no "Install Docker now?" "Y"; then
      install_docker "$sudo_cmd"
    else
      fail "Docker is required. Install Docker first and rerun this script."
    fi
  fi

  if ask_yes_no "Configure Docker registry mirror ($DOCKER_MIRROR)?" "Y"; then
    configure_docker_mirror "$sudo_cmd"
  else
    info "Skipping Docker registry mirror configuration."
  fi

  configure_docker_command "$sudo_cmd"
  configure_opensearch_kernel "$sudo_cmd"
  prepare_repo "$sudo_cmd"
  write_env_file
  start_services

  cat <<'MSG'

Voyager Docs has been started.

Open in browser:
  http://SERVER_PUBLIC_IP

Initial accounts:
  admin / 12345678
  employee / 12345678

After first login:
  1. Change the admin password.
  2. Configure the Bailian API Key in System Settings.

Useful commands:
  cd /opt/voyager/repo
  docker compose --env-file .env -f docker-compose.prod.yml ps
  docker compose --env-file .env -f docker-compose.prod.yml logs -f

MSG
}

main "$@"

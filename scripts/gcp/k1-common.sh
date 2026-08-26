#!/usr/bin/env bash

set -euo pipefail

readonly K1_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly K1_CONFIG_FILE="${K1_CONFIG_FILE:-$K1_REPO_ROOT/deploy/gcp/k1.env}"

k1_die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

k1_info() {
  printf 'K1: %s\n' "$*"
}

k1_require_command() {
  command -v "$1" >/dev/null 2>&1 || k1_die "required command not found: $1"
}

k1_load_config() {
  [[ -f "$K1_CONFIG_FILE" ]] || k1_die "config not found: $K1_CONFIG_FILE (copy deploy/gcp/k1.env.example)"
  # shellcheck disable=SC1090
  source "$K1_CONFIG_FILE"

  local required=(
    K1_GCP_PROJECT_ID K1_GCP_REGION K1_RESOURCE_PREFIX K1_ARTIFACT_REPOSITORY
    K1_CLOUD_RUN_SERVICE K1_RUNTIME_SERVICE_ACCOUNT K1_CLOUD_SQL_INSTANCE
    K1_DATABASE_NAME K1_REDIS_INSTANCE K1_VPC_NETWORK K1_VPC_SUBNET K1_VPC_SUBNET_RANGE
    K1_IMAGE_TAG K1_CLOUD_SQL_TIER K1_CLOUD_SQL_DISK_GB K1_REDIS_SIZE_GB
    K1_CLOUD_RUN_MEMORY K1_CLOUD_RUN_CPU K1_CLOUD_RUN_MIN_INSTANCES
    K1_CLOUD_RUN_MAX_INSTANCES K1_INVOKER_MODE K1_SECRET_FMP_API_KEY
    K1_SECRET_DATABASE_USERNAME K1_SECRET_DATABASE_PASSWORD
    K1_SECRET_JWT_PRIVATE_KEY K1_SECRET_JWT_PUBLIC_KEY
  )
  local name
  for name in "${required[@]}"; do
    [[ -n "${!name:-}" ]] || k1_die "required config is empty: $name"
    [[ "${!name}" != replace-* ]] || k1_die "placeholder must be replaced: $name"
  done

  [[ "$K1_CLOUD_RUN_MAX_INSTANCES" == "1" ]] || \
    k1_die "K1 requires K1_CLOUD_RUN_MAX_INSTANCES=1 while scheduled jobs run in-process"
  [[ "$K1_INVOKER_MODE" == "public" || "$K1_INVOKER_MODE" == "authenticated" ]] || \
    k1_die "K1_INVOKER_MODE must be public or authenticated"
  [[ "${K1_ALERT_EMAIL_ENABLED:-false}" == "true" || "${K1_ALERT_EMAIL_ENABLED:-false}" == "false" ]] || \
    k1_die "K1_ALERT_EMAIL_ENABLED must be true or false"
  [[ -n "${K1_NOTIFICATION_CHANNEL:-}" || -n "${K1_ALERT_EMAIL:-}" ]] || \
    k1_die "set K1_NOTIFICATION_CHANNEL or K1_ALERT_EMAIL for Cloud Monitoring alerts"
}

k1_project_number() {
  gcloud projects describe "$K1_GCP_PROJECT_ID" --format='value(projectNumber)'
}

k1_runtime_service_account_email() {
  printf '%s@%s.iam.gserviceaccount.com' "$K1_RUNTIME_SERVICE_ACCOUNT" "$K1_GCP_PROJECT_ID"
}

k1_image_uri() {
  printf '%s-docker.pkg.dev/%s/%s/vis-backend:%s' \
    "$K1_GCP_REGION" "$K1_GCP_PROJECT_ID" "$K1_ARTIFACT_REPOSITORY" "$K1_IMAGE_TAG"
}

k1_secret_exists() {
  gcloud secrets describe "$1" --project "$K1_GCP_PROJECT_ID" >/dev/null 2>&1
}

k1_require_secret_version() {
  local secret="$1"
  k1_secret_exists "$secret" || k1_die "Secret Manager container missing: $secret"
  local version
  version="$(gcloud secrets versions list "$secret" --project "$K1_GCP_PROJECT_ID" \
    --filter='state=ENABLED' --limit=1 --format='value(name)')"
  [[ -n "$version" ]] || k1_die "no enabled version for secret: $secret"
}

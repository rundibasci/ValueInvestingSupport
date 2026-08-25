#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/k1-common.sh"

k1_require_command gcloud
k1_load_config

[[ "${K1_CONFIRM_BILLABLE_PROVISIONING:-}" == "YES" ]] || \
  k1_die "set K1_CONFIRM_BILLABLE_PROVISIONING=YES only after reviewing region, sizes, pricing, and cleanup"

k1_info "enabling required APIs"
gcloud services enable \
  run.googleapis.com artifactregistry.googleapis.com sqladmin.googleapis.com \
  redis.googleapis.com secretmanager.googleapis.com vpcaccess.googleapis.com \
  servicenetworking.googleapis.com monitoring.googleapis.com logging.googleapis.com \
  --project "$K1_GCP_PROJECT_ID"

if ! gcloud artifacts repositories describe "$K1_ARTIFACT_REPOSITORY" \
  --location "$K1_GCP_REGION" --project "$K1_GCP_PROJECT_ID" >/dev/null 2>&1; then
  gcloud artifacts repositories create "$K1_ARTIFACT_REPOSITORY" --repository-format=docker \
    --location "$K1_GCP_REGION" --description="VIS K1 immutable application images" \
    --project "$K1_GCP_PROJECT_ID"
fi

runtime_email="$(k1_runtime_service_account_email)"
if ! gcloud iam service-accounts describe "$runtime_email" --project "$K1_GCP_PROJECT_ID" >/dev/null 2>&1; then
  gcloud iam service-accounts create "$K1_RUNTIME_SERVICE_ACCOUNT" \
    --display-name="VIS K1 Cloud Run runtime" --project "$K1_GCP_PROJECT_ID"
fi

for role in roles/cloudsql.client roles/logging.logWriter roles/monitoring.metricWriter; do
  gcloud projects add-iam-policy-binding "$K1_GCP_PROJECT_ID" \
    --member="serviceAccount:$runtime_email" --role="$role" --condition=None --quiet >/dev/null
done

if ! gcloud compute networks describe "$K1_VPC_NETWORK" --project "$K1_GCP_PROJECT_ID" >/dev/null 2>&1; then
  gcloud compute networks create "$K1_VPC_NETWORK" --subnet-mode=auto --project "$K1_GCP_PROJECT_ID"
fi

private_service_range="$K1_RESOURCE_PREFIX-managed-services"
if ! gcloud compute addresses describe "$private_service_range" --global \
  --project "$K1_GCP_PROJECT_ID" >/dev/null 2>&1; then
  gcloud compute addresses create "$private_service_range" --global --purpose=VPC_PEERING \
    --prefix-length=16 --network "$K1_VPC_NETWORK" --project "$K1_GCP_PROJECT_ID"
fi
if ! gcloud services vpc-peerings list --network "$K1_VPC_NETWORK" --project "$K1_GCP_PROJECT_ID" \
  --format='value(service)' | grep -qx 'servicenetworking.googleapis.com'; then
  gcloud services vpc-peerings connect --service=servicenetworking.googleapis.com \
    --ranges "$private_service_range" --network "$K1_VPC_NETWORK" --project "$K1_GCP_PROJECT_ID"
fi

if ! gcloud compute networks vpc-access connectors describe "$K1_VPC_CONNECTOR" \
  --region "$K1_GCP_REGION" --project "$K1_GCP_PROJECT_ID" >/dev/null 2>&1; then
  gcloud compute networks vpc-access connectors create "$K1_VPC_CONNECTOR" \
    --region "$K1_GCP_REGION" --network "$K1_VPC_NETWORK" --range=10.8.0.0/28 \
    --min-instances "$K1_VPC_CONNECTOR_MIN_INSTANCES" \
    --max-instances "$K1_VPC_CONNECTOR_MAX_INSTANCES" --project "$K1_GCP_PROJECT_ID"
fi

if ! gcloud sql instances describe "$K1_CLOUD_SQL_INSTANCE" --project "$K1_GCP_PROJECT_ID" >/dev/null 2>&1; then
  gcloud sql instances create "$K1_CLOUD_SQL_INSTANCE" --database-version=POSTGRES_16 \
    --tier "$K1_CLOUD_SQL_TIER" --region "$K1_GCP_REGION" \
    --storage-size "$K1_CLOUD_SQL_DISK_GB" --storage-type=SSD --availability-type=zonal \
    --no-assign-ip --network "$K1_VPC_NETWORK" --project "$K1_GCP_PROJECT_ID"
fi

if ! gcloud sql databases describe "$K1_DATABASE_NAME" --instance "$K1_CLOUD_SQL_INSTANCE" \
  --project "$K1_GCP_PROJECT_ID" >/dev/null 2>&1; then
  gcloud sql databases create "$K1_DATABASE_NAME" --instance "$K1_CLOUD_SQL_INSTANCE" \
    --project "$K1_GCP_PROJECT_ID"
fi

if ! gcloud redis instances describe "$K1_REDIS_INSTANCE" --region "$K1_GCP_REGION" \
  --project "$K1_GCP_PROJECT_ID" >/dev/null 2>&1; then
  gcloud redis instances create "$K1_REDIS_INSTANCE" --region "$K1_GCP_REGION" \
    --size "$K1_REDIS_SIZE_GB" --tier=basic --redis-version=redis_7_0 \
    --network "$K1_VPC_NETWORK" --connect-mode=direct-peering --project "$K1_GCP_PROJECT_ID"
fi

secrets=("$K1_SECRET_FMP_API_KEY" "$K1_SECRET_DATABASE_USERNAME" "$K1_SECRET_DATABASE_PASSWORD" \
  "$K1_SECRET_JWT_PRIVATE_KEY" "$K1_SECRET_JWT_PUBLIC_KEY")
if [[ "${K1_ALERT_EMAIL_ENABLED:-false}" == "true" ]]; then
  secrets+=("$K1_SECRET_SMTP_USERNAME" "$K1_SECRET_SMTP_PASSWORD")
fi
for secret in "${secrets[@]}"; do
  if ! k1_secret_exists "$secret"; then
    gcloud secrets create "$secret" --replication-policy=automatic --project "$K1_GCP_PROJECT_ID"
  fi
  gcloud secrets add-iam-policy-binding "$secret" --project "$K1_GCP_PROJECT_ID" \
    --member="serviceAccount:$runtime_email" --role=roles/secretmanager.secretAccessor --quiet >/dev/null
done

k1_info "provisioning completed; no secret values were created"
k1_info "create database user and secret versions interactively before deployment; see docs/operations/k1-gcp-runbook.md"

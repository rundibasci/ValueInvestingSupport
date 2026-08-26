#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/k1-common.sh"

k1_require_command gcloud
k1_load_config

[[ "${K1_CONFIRM_DEPLOY:-}" == "YES" ]] || k1_die "set K1_CONFIRM_DEPLOY=YES only after the pre-deploy review"

core_secrets=("$K1_SECRET_FMP_API_KEY" "$K1_SECRET_DATABASE_USERNAME" "$K1_SECRET_DATABASE_PASSWORD" \
  "$K1_SECRET_JWT_PRIVATE_KEY" "$K1_SECRET_JWT_PUBLIC_KEY")
for secret in "${core_secrets[@]}"; do
  k1_require_secret_version "$secret"
done
if [[ "${K1_ALERT_EMAIL_ENABLED:-false}" == "true" ]]; then
  k1_require_secret_version "$K1_SECRET_SMTP_USERNAME"
  k1_require_secret_version "$K1_SECRET_SMTP_PASSWORD"
fi

connection_name="$(gcloud sql instances describe "$K1_CLOUD_SQL_INSTANCE" \
  --project "$K1_GCP_PROJECT_ID" --format='value(connectionName)')"
redis_host="$(gcloud redis instances describe "$K1_REDIS_INSTANCE" --region "$K1_GCP_REGION" \
  --project "$K1_GCP_PROJECT_ID" --format='value(host)')"
runtime_email="$(k1_runtime_service_account_email)"
database_url="jdbc:postgresql:///$K1_DATABASE_NAME?cloudSqlInstance=$connection_name&socketFactory=com.google.cloud.sql.postgres.SocketFactory&ipTypes=PRIVATE"

env_vars="SPRING_PROFILES_ACTIVE=prod,APP_DEPLOYMENT_MODE=k1,APP_DECLARED_MAX_INSTANCES=1,MARKET_DATA_SOURCE=fmp,DATABASE_URL=$database_url,REDIS_HOST=$redis_host,REDIS_PORT=6379,ALERT_EMAIL_ENABLED=${K1_ALERT_EMAIL_ENABLED:-false},MANAGEMENT_HEALTH_MAIL_ENABLED=${K1_ALERT_EMAIL_ENABLED:-false},MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true,MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always,ALERT_EMAIL_FROM=${K1_ALERT_EMAIL_FROM:-},SPRING_MAIL_HOST=${K1_SPRING_MAIL_HOST:-},SPRING_MAIL_PORT=${K1_SPRING_MAIL_PORT:-587}"
secret_vars="FMP_API_KEY=$K1_SECRET_FMP_API_KEY:latest,DATABASE_USERNAME=$K1_SECRET_DATABASE_USERNAME:latest,DATABASE_PASSWORD=$K1_SECRET_DATABASE_PASSWORD:latest,JWT_PRIVATE_KEY=$K1_SECRET_JWT_PRIVATE_KEY:latest,JWT_PUBLIC_KEY=$K1_SECRET_JWT_PUBLIC_KEY:latest"
if [[ "${K1_ALERT_EMAIL_ENABLED:-false}" == "true" ]]; then
  secret_vars+=",SPRING_MAIL_USERNAME=$K1_SECRET_SMTP_USERNAME:latest,SPRING_MAIL_PASSWORD=$K1_SECRET_SMTP_PASSWORD:latest"
fi

traffic_args=(--tag candidate)
if gcloud run services describe "$K1_CLOUD_RUN_SERVICE" --region "$K1_GCP_REGION" \
  --project "$K1_GCP_PROJECT_ID" >/dev/null 2>&1; then
  traffic_args+=(--no-traffic)
else
  k1_info "initial service creation cannot use --no-traffic; validate before stakeholder use"
fi

gcloud run deploy "$K1_CLOUD_RUN_SERVICE" --image "$(k1_image_uri)" --region "$K1_GCP_REGION" \
  --project "$K1_GCP_PROJECT_ID" --platform=managed --service-account "$runtime_email" \
  --cpu "$K1_CLOUD_RUN_CPU" --memory "$K1_CLOUD_RUN_MEMORY" \
  --min-instances "$K1_CLOUD_RUN_MIN_INSTANCES" --max-instances "$K1_CLOUD_RUN_MAX_INSTANCES" \
  --concurrency=40 --timeout=300 --port=8080 --execution-environment=gen2 \
  --startup-probe=httpGet.path=/actuator/health/liveness,httpGet.port=8080,initialDelaySeconds=10,timeoutSeconds=5,periodSeconds=10,failureThreshold=18 \
  --add-cloudsql-instances "$connection_name" --network "$K1_VPC_NETWORK" --subnet "$K1_VPC_SUBNET" \
  --network-tags="$K1_RESOURCE_PREFIX-api" \
  --vpc-egress=private-ranges-only --set-env-vars "$env_vars" --set-secrets "$secret_vars" \
  "${traffic_args[@]}" --quiet

if [[ "$K1_INVOKER_MODE" == "public" ]]; then
  gcloud run services add-iam-policy-binding "$K1_CLOUD_RUN_SERVICE" --region "$K1_GCP_REGION" \
    --project "$K1_GCP_PROJECT_ID" --member=allUsers --role=roles/run.invoker
fi

if [[ " ${traffic_args[*]} " == *" --no-traffic "* ]]; then
  k1_info "candidate revision deployed with no traffic"
  k1_info "verify the tagged candidate, then explicitly promote traffic per the runbook"
else
  k1_info "initial revision deployed; verify immediately before stakeholder use"
fi

#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/k1-common.sh"

k1_require_command gcloud
k1_require_command curl
k1_require_command jq
k1_load_config

service_json="$(gcloud run services describe "$K1_CLOUD_RUN_SERVICE" --region "$K1_GCP_REGION" \
  --project "$K1_GCP_PROJECT_ID" --format=json)"
max_instances="$(jq -r '.metadata.annotations["autoscaling.knative.dev/maxScale"] // .spec.template.metadata.annotations["autoscaling.knative.dev/maxScale"] // empty' <<<"$service_json")"
[[ "$max_instances" == "1" ]] || k1_die "deployed max instance count is not 1 (found: ${max_instances:-missing})"

service_url="$(jq -r '.status.url' <<<"$service_json")"
[[ -n "$service_url" && "$service_url" != null ]] || k1_die "Cloud Run service URL is unavailable"

curl_args=(--fail --silent --show-error --max-time 30)
if [[ "$K1_INVOKER_MODE" == "authenticated" ]]; then
  identity_token="$(gcloud auth print-identity-token)"
  curl_args+=(-H "Authorization: Bearer $identity_token")
fi
health="$(curl "${curl_args[@]}" "$service_url/actuator/health")"
status="$(jq -r '.status' <<<"$health")"
[[ "$status" == "UP" ]] || k1_die "health status is not UP"

k1_info "service=$service_url health=UP max-instances=1"
k1_info "run authenticated application smoke tests and log/secret review using docs/operations/k1-gcp-runbook.md"

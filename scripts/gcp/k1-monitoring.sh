#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/k1-common.sh"

k1_require_command gcloud
k1_require_command jq
k1_load_config

[[ -n "${K1_NOTIFICATION_CHANNEL:-}" ]] || k1_die "K1_NOTIFICATION_CHANNEL is required"
[[ "${K1_CONFIRM_MONITORING_CHANGES:-}" == "YES" ]] || \
  k1_die "set K1_CONFIRM_MONITORING_CHANGES=YES after reviewing the notification target"

service_json="$(gcloud run services describe "$K1_CLOUD_RUN_SERVICE" --region "$K1_GCP_REGION" \
  --project "$K1_GCP_PROJECT_ID" --format=json)"
service_url="$(jq -r '.status.url' <<<"$service_json")"
host="${service_url#https://}"
[[ -n "$host" && "$host" != "$service_url" ]] || k1_die "expected an HTTPS Cloud Run URL"

uptime_name="$K1_RESOURCE_PREFIX-health"
uptime_id="$(gcloud monitoring uptime list-configs --project "$K1_GCP_PROJECT_ID" \
  --filter="displayName=$uptime_name" --format='value(name.basename())' | head -n 1)"
if [[ -z "$uptime_id" ]]; then
  auth_args=()
  if [[ "$K1_INVOKER_MODE" == "authenticated" ]]; then
    auth_args+=(--service-agent-auth=oidc-token)
  fi
  gcloud monitoring uptime create "$uptime_name" --project "$K1_GCP_PROJECT_ID" \
    --resource-type=uptime-url --resource-labels="host=$host,project_id=$K1_GCP_PROJECT_ID" \
    --protocol=https --path=/actuator/health --request-method=get --validate-ssl \
    --status-codes=200 --matcher-content='"status":"UP"' --matcher-type=contains-string \
    --period=5 --timeout=10 "${auth_args[@]}"
  uptime_id="$(gcloud monitoring uptime list-configs --project "$K1_GCP_PROJECT_ID" \
    --filter="displayName=$uptime_name" --format='value(name.basename())' | head -n 1)"
fi
[[ -n "$uptime_id" ]] || k1_die "uptime check was not found after creation"

if [[ "$K1_INVOKER_MODE" == "authenticated" ]]; then
  monitoring_agent="service-$(k1_project_number)@gcp-sa-monitoring-notification.iam.gserviceaccount.com"
  gcloud run services add-iam-policy-binding "$K1_CLOUD_RUN_SERVICE" --region "$K1_GCP_REGION" \
    --project "$K1_GCP_PROJECT_ID" --member="serviceAccount:$monitoring_agent" \
    --role=roles/run.invoker --quiet >/dev/null
fi

policy_name="$K1_RESOURCE_PREFIX-health-unavailable"
existing_policy="$(gcloud monitoring policies list --project "$K1_GCP_PROJECT_ID" \
  --filter="displayName=$policy_name" --format='value(name)' | head -n 1)"
if [[ -z "$existing_policy" ]]; then
  metric_filter="resource.type=\"uptime_url\" AND metric.type=\"monitoring.googleapis.com/uptime_check/check_passed\" AND metric.label.check_id=\"$uptime_id\""
  gcloud monitoring policies create --project "$K1_GCP_PROJECT_ID" \
    --display-name="$policy_name" --combiner=OR \
    --condition-display-name="VIS K1 health check failed" --condition-filter="$metric_filter" \
    --if='< 1' --duration=120s --trigger-count=1 \
    --notification-channels="$K1_NOTIFICATION_CHANNEL" \
    --documentation="Internal K1 stakeholder service health check failed. Follow docs/operations/k1-gcp-runbook.md."
fi

k1_info "uptime-check=$uptime_id alert-policy=$policy_name notification-channel=$K1_NOTIFICATION_CHANNEL"

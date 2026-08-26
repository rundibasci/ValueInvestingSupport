#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/k1-common.sh"

k1_require_command gcloud
k1_require_command jq
k1_load_config

[[ "${K1_CONFIRM_MONITORING_CHANGES:-}" == "YES" ]] || \
  k1_die "set K1_CONFIRM_MONITORING_CHANGES=YES after reviewing the notification target"

service_json="$(gcloud run services describe "$K1_CLOUD_RUN_SERVICE" --region "$K1_GCP_REGION" \
  --project "$K1_GCP_PROJECT_ID" --format=json)"
service_url="$(jq -r '.status.url' <<<"$service_json")"
host="${service_url#https://}"
[[ -n "$host" && "$host" != "$service_url" ]] || k1_die "expected an HTTPS Cloud Run URL"
revision_name="$(jq -r '.status.latestReadyRevisionName' <<<"$service_json")"
[[ -n "$revision_name" && "$revision_name" != null ]] || k1_die "latest ready revision is unavailable"

if [[ "$K1_INVOKER_MODE" == "authenticated" ]]; then
  monitoring_agent="service-$(k1_project_number)@gcp-sa-monitoring-notification.iam.gserviceaccount.com"
  gcloud run services add-iam-policy-binding "$K1_CLOUD_RUN_SERVICE" --region "$K1_GCP_REGION" \
    --project "$K1_GCP_PROJECT_ID" --member="serviceAccount:$monitoring_agent" \
    --role=roles/run.invoker --quiet >/dev/null
fi

uptime_name="$K1_RESOURCE_PREFIX-health"
uptime_id="$(gcloud monitoring uptime list-configs --project "$K1_GCP_PROJECT_ID" \
  --filter="displayName=$uptime_name" --format='value(name.basename())' | head -n 1)"
if [[ -z "$uptime_id" ]]; then
  auth_args=()
  target_args=(--resource-type=uptime-url \
    --resource-labels="host=$host,project_id=$K1_GCP_PROJECT_ID")
  if [[ "$K1_INVOKER_MODE" == "authenticated" ]]; then
    auth_args+=(--service-agent-auth=oidc-token)
    target_args=(--resource-type=cloud-run-revision \
      --resource-labels="project_id=$K1_GCP_PROJECT_ID,location=$K1_GCP_REGION,service_name=$K1_CLOUD_RUN_SERVICE,revision_name=$revision_name,configuration_name=$K1_CLOUD_RUN_SERVICE")
  fi
  gcloud monitoring uptime create "$uptime_name" --project "$K1_GCP_PROJECT_ID" \
    "${target_args[@]}" \
    --protocol=https --path=/actuator/health/liveness --request-method=get --validate-ssl=true \
    --status-codes=200 --matcher-content='"status":"UP"' --matcher-type=contains-string \
    --period=5 --timeout=10 "${auth_args[@]}"
  uptime_id="$(gcloud monitoring uptime list-configs --project "$K1_GCP_PROJECT_ID" \
    --filter="displayName=$uptime_name" --format='value(name.basename())' | head -n 1)"
fi
[[ -n "$uptime_id" ]] || k1_die "uptime check was not found after creation"

policy_name="$K1_RESOURCE_PREFIX-health-unavailable"
notification_channel="${K1_NOTIFICATION_CHANNEL:-}"
if [[ -z "$notification_channel" ]]; then
  notification_channel="$(gcloud beta monitoring channels list --project "$K1_GCP_PROJECT_ID" \
    --filter="type=email AND labels.email_address=$K1_ALERT_EMAIL" --format='value(name)' | head -n 1)"
  if [[ -z "$notification_channel" ]]; then
    notification_channel="$(gcloud beta monitoring channels create --project "$K1_GCP_PROJECT_ID" \
      --display-name="$K1_RESOURCE_PREFIX-operator-email" \
      --description="VIS K1 internal stakeholder deployment operator" --type=email \
      --channel-labels="email_address=$K1_ALERT_EMAIL" --format='value(name)')"
  fi
fi
[[ -n "$notification_channel" ]] || k1_die "monitoring notification channel is unavailable"
existing_policy="$(gcloud monitoring policies list --project "$K1_GCP_PROJECT_ID" \
  --filter="displayName=$policy_name" --format='value(name)' | head -n 1)"
if [[ -z "$existing_policy" ]]; then
  metric_filter="resource.type=\"uptime_url\" AND metric.type=\"monitoring.googleapis.com/uptime_check/check_passed\" AND metric.label.check_id=\"$uptime_id\""
  gcloud monitoring policies create --project "$K1_GCP_PROJECT_ID" \
    --display-name="$policy_name" --combiner=OR \
    --condition-display-name="VIS K1 health check failed" --condition-filter="$metric_filter" \
    --if='< 1' --duration=120s --trigger-count=1 \
    --notification-channels="$notification_channel" \
    --documentation="Internal K1 stakeholder service health check failed. Follow docs/operations/k1-gcp-runbook.md."
fi

k1_info "uptime-check=$uptime_id alert-policy=$policy_name notification-channel=$notification_channel"
